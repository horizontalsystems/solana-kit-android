package io.horizontalsystems.solanakit.transactions

import android.util.Log
import com.metaplex.lib.programs.token_metadata.TokenMetadataProgram
import com.metaplex.lib.programs.token_metadata.accounts.MetadataAccount
import com.metaplex.lib.programs.token_metadata.accounts.MetaplexTokenStandard.FungibleAsset
import com.metaplex.lib.programs.token_metadata.accounts.MetaplexTokenStandard.NonFungible
import com.metaplex.lib.programs.token_metadata.accounts.MetaplexTokenStandard.NonFungibleEdition
import com.solana.api.Api
import com.solana.api.getMultipleAccounts
import com.solana.core.PublicKey
import com.solana.models.buffer.BufferInfo
import com.solana.models.buffer.Mint
import com.solana.programs.TokenProgram
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTokenTransfer
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.LastSyncedTransaction
import io.horizontalsystems.solanakit.models.MintAccount
import io.horizontalsystems.solanakit.models.TokenAccount
import io.horizontalsystems.solanakit.models.TokenTransfer
import io.horizontalsystems.solanakit.models.Transaction
import io.horizontalsystems.solanakit.noderpc.NftClient
import io.horizontalsystems.solanakit.noderpc.endpoints.SignatureInfo
import io.horizontalsystems.solanakit.noderpc.endpoints.getSignaturesForAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface ITransactionListener {
    fun onUpdateTransactionSyncState(syncState: SolanaKit.SyncState)
}

class TransactionSyncer(
    private val publicKey: PublicKey,
    private val rpcClient: Api,
    private val httpClient: OkHttpClient,
    private val nftClient: NftClient,
    private val storage: TransactionStorage,
    private val transactionManager: TransactionManager,
    private val pendingTransactionSyncer: PendingTransactionSyncer
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

        pendingTransactionSyncer.sync()

        val lastTransactionHash = storage.lastNonPendingTransaction()?.hash

        try {
            val rpcSignatureInfos = getSignaturesFromRpcNode(lastTransactionHash)

            if (rpcSignatureInfos.isEmpty()) {
                syncState = SolanaKit.SyncState.Synced()
                return
            }

            val fullTransactions = mutableListOf<FullTransaction>()
            val allMintAddresses = mutableSetOf<String>()
            val tokenAccountsSet = mutableSetOf<TokenAccount>()

            val signatures = rpcSignatureInfos.map { it.signature }
            val txResponses = fetchTransactionsBatch(signatures)

            for (signatureInfo in rpcSignatureInfos) {
                val txResponse = txResponses[signatureInfo.signature] ?: continue
                val parsed = parseTransaction(signatureInfo.signature, txResponse)
                fullTransactions.add(parsed.fullTransaction)
                allMintAddresses.addAll(parsed.mintAddresses)
                tokenAccountsSet.addAll(parsed.tokenAccounts)
            }

            val mintAccounts = try {
                getMintAccounts(allMintAddresses.toList())
            } catch (e: Throwable) {
                emptyMap()
            }

            val resolvedTransactions = fullTransactions.map { fullTx ->
                val resolvedTokenTransfers = fullTx.tokenTransfers.map { ftt ->
                    val mintAccount = mintAccounts[ftt.tokenTransfer.mintAddress]
                        ?: MintAccount(ftt.tokenTransfer.mintAddress, ftt.mintAccount.decimals)
                    FullTokenTransfer(ftt.tokenTransfer, mintAccount)
                }
                FullTransaction(fullTx.transaction, resolvedTokenTransfers)
            }

            val resolvedTokenAccounts = tokenAccountsSet.toMutableList()

            transactionManager.handle(resolvedTransactions, resolvedTokenAccounts)

            if (rpcSignatureInfos.isNotEmpty()) {
                storage.setSyncedBlockTime(LastSyncedTransaction(rpcSyncSourceName, rpcSignatureInfos.first().signature))
            }

            syncState = SolanaKit.SyncState.Synced()
        } catch (exception: Throwable) {
            syncState = SolanaKit.SyncState.NotSynced(exception)
        }
    }

    private suspend fun fetchTransactionsBatch(signatures: List<String>): Map<String, TransactionResponse> {
        if (signatures.isEmpty()) return emptyMap()

        val rpcUrl = rpcClient.router.endpoint.url
        val results = mutableMapOf<String, TransactionResponse>()

        for (chunk in signatures.chunked(batchChunkSize)) {
            val chunkResults = executeBatchRequest(chunk, rpcUrl)
            results.putAll(chunkResults)
        }

        return results
    }

    private suspend fun executeBatchRequest(
        signatures: List<String>,
        rpcUrl: URL
    ): Map<String, TransactionResponse> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("[")
        for ((index, signature) in signatures.withIndex()) {
            if (index > 0) sb.append(",")
            sb.append("""{"jsonrpc":"2.0","id":$index,"method":"getTransaction","params":["$signature",{"encoding":"jsonParsed","maxSupportedTransactionVersion":0}]}""")
        }
        sb.append("]")

        val request = Request.Builder()
            .url(rpcUrl)
            .post(sb.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext executeIndividualRequests(signatures, rpcUrl)
            }
            val body = resp.body?.string() ?: return@withContext emptyMap()
            parseBatchResponse(signatures, body)
        }
    }

    private suspend fun executeIndividualRequests(
        signatures: List<String>,
        rpcUrl: URL
    ): Map<String, TransactionResponse> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, TransactionResponse>()
        val adapter = moshi.adapter(TransactionResponse::class.java)

        for (signature in signatures) {
            try {
                val body = """{"jsonrpc":"2.0","id":0,"method":"getTransaction","params":["$signature",{"encoding":"jsonParsed","maxSupportedTransactionVersion":0}]}"""
                val request = Request.Builder()
                    .url(rpcUrl)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        return@use
                    }
                    val responseBody = resp.body?.string() ?: return@use
                    val json = JSONObject(responseBody)
                    if (json.isNull("result")) return@use
                    val txResponse = adapter.fromJson(json.getJSONObject("result").toString()) ?: return@use
                    results[signature] = txResponse
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to fetch transaction $signature", e)
            }
        }

        results
    }

    private fun parseBatchResponse(
        signatures: List<String>,
        responseBody: String
    ): Map<String, TransactionResponse> {
        val results = mutableMapOf<String, TransactionResponse>()
        val jsonArray = JSONArray(responseBody)
        val adapter = moshi.adapter(TransactionResponse::class.java)

        for (i in 0 until jsonArray.length()) {
            try {
                val item = jsonArray.getJSONObject(i)
                val id = item.optInt("id", -1)
                if (id < 0 || id >= signatures.size) continue
                if (item.isNull("result")) continue

                val resultObj = item.getJSONObject("result")
                val txResponse = adapter.fromJson(resultObj.toString()) ?: continue
                results[signatures[id]] = txResponse
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to parse batch response item $i", e)
                continue
            }
        }

        return results
    }

    private data class ParsedTransaction(
        val fullTransaction: FullTransaction,
        val mintAddresses: Set<String>,
        val tokenAccounts: Set<TokenAccount>
    )

    private fun parseTransaction(signature: String, response: TransactionResponse): ParsedTransaction {
        val meta = response.meta
        val blockTime = response.blockTime ?: 0L
        val accountKeys = response.transaction?.message?.accountKeys?.map { it.pubkey } ?: emptyList()
        val ourAddress = publicKey.toBase58()

        val ourIndex = accountKeys.indexOf(ourAddress)
        val fee = meta?.fee ?: 0L

        var solFrom: String? = null
        var solTo: String? = null
        var solAmount: BigDecimal? = null

        if (ourIndex >= 0 && meta != null && ourIndex < meta.preBalances.size && ourIndex < meta.postBalances.size) {
            val balanceChange = meta.postBalances[ourIndex] - meta.preBalances[ourIndex]
            val adjustedChange = if (ourIndex == 0) balanceChange + fee else balanceChange

            if (adjustedChange != 0L) {
                solAmount = BigDecimal(adjustedChange).abs()
                if (adjustedChange > 0) {
                    solTo = ourAddress
                    solFrom = findCounterparty(meta.preBalances, meta.postBalances, accountKeys, ourIndex, incoming = true)
                } else {
                    solFrom = ourAddress
                    solTo = findCounterparty(meta.preBalances, meta.postBalances, accountKeys, ourIndex, incoming = false)
                }
            }
        }

        val tokenTransfers = mutableListOf<FullTokenTransfer>()
        val mintAddresses = mutableSetOf<String>()
        val tokenAccounts = mutableSetOf<TokenAccount>()

        if (meta != null) {
            val postTokenBalances = meta.postTokenBalances ?: emptyList()
            val preTokenBalances = meta.preTokenBalances ?: emptyList()

            val postByKey = postTokenBalances.associateBy { "${it.accountIndex}_${it.mint}" }
            val preByKey = preTokenBalances.associateBy { "${it.accountIndex}_${it.mint}" }

            val allKeys = (postByKey.keys + preByKey.keys).toSet()

            for (key in allKeys) {
                val postBalance = postByKey[key]
                val preBalance = preByKey[key]

                val owner = postBalance?.owner ?: preBalance?.owner ?: continue
                if (owner != ourAddress) continue

                val mint = postBalance?.mint ?: preBalance?.mint ?: continue
                val decimals = postBalance?.uiTokenAmount?.decimals ?: preBalance?.uiTokenAmount?.decimals ?: 0

                val postAmount = postBalance?.uiTokenAmount?.amount?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val preAmount = preBalance?.uiTokenAmount?.amount?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val change = postAmount - preAmount

                if (change.compareTo(BigDecimal.ZERO) == 0) continue

                val incoming = change > BigDecimal.ZERO
                val tokenTransfer = TokenTransfer(signature, mint, incoming, change.abs())
                val placeholderMintAccount = MintAccount(mint, decimals)
                tokenTransfers.add(FullTokenTransfer(tokenTransfer, placeholderMintAccount))
                mintAddresses.add(mint)

                val accountIndex = postBalance?.accountIndex ?: preBalance?.accountIndex ?: continue
                if (accountIndex < accountKeys.size) {
                    tokenAccounts.add(TokenAccount(accountKeys[accountIndex], mint, BigDecimal.ZERO, decimals))
                }
            }
        }

        val error = meta?.err?.toString()

        val transaction = Transaction(
            hash = signature,
            timestamp = blockTime,
            fee = BigDecimal(fee).movePointLeft(9),
            from = solFrom,
            to = solTo,
            amount = solAmount,
            error = error,
            pending = false
        )

        return ParsedTransaction(
            FullTransaction(transaction, tokenTransfers),
            mintAddresses,
            tokenAccounts
        )
    }

    private fun findCounterparty(
        preBalances: List<Long>,
        postBalances: List<Long>,
        accountKeys: List<String>,
        ourIndex: Int,
        incoming: Boolean
    ): String? {
        var bestIndex = -1
        var bestChange = 0L

        for (i in accountKeys.indices) {
            if (i == ourIndex) continue
            val change = postBalances[i] - preBalances[i]
            if (incoming && change < bestChange) {
                bestChange = change
                bestIndex = i
            } else if (!incoming && change > bestChange) {
                bestChange = change
                bestIndex = i
            }
        }

        return if (bestIndex >= 0) accountKeys[bestIndex] else null
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
                mintAddress, mint.decimals, mint.supply,
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

    companion object {
        private const val TAG = "TransactionSyncer"
        val tokenProgramId = TokenProgram.PROGRAM_ID.toBase58()
        val tokenMetadataProgramId = TokenMetadataProgram.publicKey.toBase58()
        const val rpcSignaturesCount = 1000
        const val batchChunkSize = 100
        const val rpcSyncSourceName = "rpc/getSignaturesForAddress"
        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

}
