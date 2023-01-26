package io.horizontalsystems.solanakit.transactions

import com.metaplex.lib.programs.token_metadata.TokenMetadataProgram
import com.metaplex.lib.programs.token_metadata.accounts.MetadataAccount
import com.metaplex.lib.programs.token_metadata.accounts.MetaplexTokenStandard.*
import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.BufferInfo
import com.solana.models.buffer.Mint
import com.solana.programs.TokenProgram
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.*
import io.horizontalsystems.solanakit.noderpc.NftClient
import io.horizontalsystems.solanakit.noderpc.endpoints.SignatureInfo
import io.horizontalsystems.solanakit.noderpc.endpoints.getSignaturesForAddress
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface ITransactionListener {
    fun onUpdateTransactionSyncState(syncState: SolanaKit.SyncState)
}

class TransactionSyncer(
    private val publicKey: PublicKey,
    private val rpcClient: Api,
    private val solscanClient: SolscanClient,
    private val nftClient: NftClient,
    private val storage: TransactionStorage,
    private val transactionManager: TransactionManager
) {
    var syncState: SolanaKit.SyncState = SolanaKit.SyncState.NotSynced(SolanaKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateTransactionSyncState(value)
            }
        }

    var listener: ITransactionListener? = null

    suspend fun sync() {
        if (syncState is SolanaKit.SyncState.Syncing) return

        syncState = SolanaKit.SyncState.Syncing()

        val lastTransactionHash = storage.lastNonPendingTransaction()?.hash

        try {
            val rpcSignatureInfos = getSignaturesFromRpcNode(lastTransactionHash)
            val solTransfers = solscanClient.solTransfers(publicKey.toBase58(), storage.getSyncedBlockTime(solscanClient.solSyncSourceName)?.hash)
            val splTransfers = solscanClient.splTransfers(publicKey.toBase58(), storage.getSyncedBlockTime(solscanClient.splSyncSourceName)?.hash)
            val solscanExportedTxs = (solTransfers + splTransfers).sortedByDescending { it.blockTime }
            val mintAddresses = solscanExportedTxs.mapNotNull { it.mintAccountAddress }.toSet().toList()
            val mintAccounts = getMintAccounts(mintAddresses)
            val tokenAccounts = buildTokenAccounts(solscanExportedTxs, mintAccounts)
            val transactions = merge(rpcSignatureInfos, solscanExportedTxs, mintAccounts)

            transactionManager.handle(transactions, tokenAccounts)

            if (solTransfers.isNotEmpty()) {
                storage.setSyncedBlockTime(LastSyncedTransaction(solscanClient.solSyncSourceName, solTransfers.first().hash))
            }

            if (splTransfers.isNotEmpty()) {
                storage.setSyncedBlockTime(LastSyncedTransaction(solscanClient.splSyncSourceName, splTransfers.first().hash))
            }

            syncState = SolanaKit.SyncState.Synced()
        } catch (exception: Throwable) {
            syncState = SolanaKit.SyncState.NotSynced(exception)
        }
    }

    private fun merge(rpcSignatureInfos: List<SignatureInfo>, solscanTxsMap: List<SolscanTransaction>, mintAccounts: Map<String, MintAccount>): List<FullTransaction> {
        val transactions = mutableMapOf<String, FullTransaction>()

        for (signatureInfo in rpcSignatureInfos) {
            signatureInfo.blockTime?.let { blockTime ->
                val transaction = Transaction(signatureInfo.signature, blockTime, error = signatureInfo.err?.toString())
                transactions[signatureInfo.signature] = FullTransaction(transaction, listOf())
            }
        }

        for ((hash, solscanTxs) in solscanTxsMap.groupBy { it.hash }) {
            try {
                val existingTransaction = transactions[hash]?.transaction
                val solscanTx = solscanTxs.first()
                val mergedTransaction = Transaction(
                    hash,
                    existingTransaction?.timestamp ?: solscanTx.blockTime,
                    solscanTx.fee?.toBigDecimalOrNull(),
                    solscanTx.solTransferSource,
                    solscanTx.solTransferDestination,
                    solscanTx.solAmount?.toBigDecimal(),
                    existingTransaction?.error
                )

                val tokenTransfers: List<FullTokenTransfer> = solscanTxs.mapNotNull { solscanTx ->
                    val mintAddress = solscanTx.mintAccountAddress ?: return@mapNotNull null
                    val mintAccount = mintAccounts[mintAddress] ?: return@mapNotNull null
                    val amount = solscanTx.splBalanceChange?.toBigDecimal() ?: return@mapNotNull null

                    FullTokenTransfer(
                        TokenTransfer(hash, mintAddress, amount > BigDecimal.ZERO, amount),
                        mintAccount
                    )
                }

                transactions[hash] = FullTransaction(mergedTransaction, tokenTransfers)
            } catch (e: Throwable) {
                continue
            }
        }

        return transactions.values.toList()
    }

    private suspend fun getSignaturesFromRpcNode(lastTransactionHash: String?): List<SignatureInfo> {
        val signatureObjects = mutableListOf<SignatureInfo>()
        var signatureObjectsChunk = listOf<SignatureInfo>()

        do {
            val lastSignature = signatureObjectsChunk.lastOrNull()?.signature
            signatureObjectsChunk = getSignaturesChunk(lastTransactionHash, lastSignature)
            signatureObjects.addAll(signatureObjectsChunk)

        } while (signatureObjectsChunk.size == rpcSignaturesCount)

        return signatureObjects
    }

    private suspend fun getSignaturesChunk(lastTransactionHash: String?, before: String? = null) = suspendCoroutine<List<SignatureInfo>> { continuation ->
        rpcClient.getSignaturesForAddress(publicKey, until = lastTransactionHash, before = before, limit = rpcSignaturesCount) { result ->
            result.onSuccess { signatureObjects ->
                continuation.resume(signatureObjects)
            }

            result.onFailure { exception ->
                continuation.resumeWithException(exception)
            }
        }
    }

    private suspend fun getMintAccounts(mintAddresses: List<String>) : Map<String, MintAccount> {
        if (mintAddresses.isEmpty()) {
            return mutableMapOf()
        }

        val publicKeys = mintAddresses.map { PublicKey.valueOf(it) }

        val mintAccountData = suspendCoroutine<List<BufferInfo<Mint>?>> { continuation ->
            rpcClient.getMultipleAccounts(publicKeys, Mint::class.java) { result ->
                result.onSuccess {
                    continuation.resume(it)
                }

                result.onFailure { exception ->
                    continuation.resumeWithException(exception)
                }
            }
        }

        val metadataAccountsMap = mutableMapOf<String, MetadataAccount>()
        nftClient.findAllByMintList(publicKeys).getOrThrow()
            .filterNotNull()
            .filter { it.owner == tokenMetadataProgramId }
            .forEach {
                val metadata = it.data?.value ?: return@forEach
                metadataAccountsMap[metadata.mint.toBase58()] = metadata
            }

        val mintAccounts = mutableMapOf<String, MintAccount>()

        for ((index, mintAddress) in mintAddresses.withIndex()) {
            val account = mintAccountData[index] ?: continue
            val owner = account.owner
            val mint = account.data?.value

            if (owner != tokenProgramId || mint == null) continue

            val metadataAccount = metadataAccountsMap[mintAddress]

            val isNft = when {
                mint.decimals != 0 -> false
                mint.supply == 1L && mint.mintAuthority == null -> true
                metadataAccount?.tokenStandard == NonFungible -> true
                metadataAccount?.tokenStandard == FungibleAsset -> true
                metadataAccount?.tokenStandard == NonFungibleEdition -> true
                else -> false
            }

            val collectionAddress = metadataAccount?.collection?.let {
                if (!it.verified) return@let null
                it.key.toBase58()
            }

            val mintAccount = MintAccount(
                mintAddress, mint.supply, mint.decimals,
                isNft,
                metadataAccount?.data?.name,
                metadataAccount?.data?.symbol,
                metadataAccount?.data?.uri,
                collectionAddress
            )

            mintAccounts[mintAddress] = mintAccount
        }

        return mintAccounts
    }

    private fun buildTokenAccounts(solscanExportedTxs: List<SolscanTransaction>, mintAccounts: Map<String, MintAccount>): List<TokenAccount> =
        solscanExportedTxs.mapNotNull { solscanTx ->
            val mintAccount = solscanTx.mintAccountAddress?.let { mintAccounts[it] } ?: return@mapNotNull null
            val tokenAccountAddress = solscanTx.tokenAccountAddress ?: return@mapNotNull null

            TokenAccount(tokenAccountAddress, mintAccount.address, BigDecimal.ZERO, mintAccount.decimals)
        }.toSet().toMutableList()

    companion object {
        val tokenProgramId = TokenProgram.PROGRAM_ID.toBase58()
        val tokenMetadataProgramId = TokenMetadataProgram.publicKey.toBase58()
        const val rpcSignaturesCount = 1000
    }

}
