package io.horizontalsystems.solanakit.transactions

import com.solana.networking.NetworkingError
import okhttp3.*
import java.io.IOException
import java.io.InputStream

class SolscanClient(
    private val httpClient: OkHttpClient
) {
    val sourceName = "solscan.io"
    private val url = "https://public-api.solscan.io/account/exportTransactions"

    fun transactions(account: String, fromTime: Long?, onComplete: (Result<List<SolscanExportedTransaction>>) -> Unit) {
        val path = "?account=${account}&type=all&fromTime=${fromTime ?: 0}&toTime=10000000000"

        val request: Request = Request.Builder().url(url + path).build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onComplete(Result.failure(RuntimeException(e)))
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { body ->
                    val result = readCsv(body.byteStream())
                    onComplete(Result.success(result))
                } ?: run {
                    onComplete(Result.failure(NetworkingError.invalidResponseNoData))
                }

            }
        })
    }

    private fun readCsv(inputStream: InputStream): List<SolscanExportedTransaction> {
        val reader = inputStream.bufferedReader()
        reader.readLine()

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .map {
                val fields = it.split(',', ignoreCase = false, limit = 16)
                SolscanExportedTransaction(
                    toNotOptionalString(fields[0]),
                    toNotOptionalString(fields[1]),
                    toNotOptionalString(fields[2]).toLong(),
                    toNotOptionalString(fields[4]),
                    toOptionalString(fields[5]),
                    toOptionalString(fields[6]),
                    toOptionalString(fields[7]),
                    toOptionalString(fields[8]),
                    toOptionalString(fields[9]),
                    toOptionalString(fields[10]),
                    toNotOptionalString(fields[11]),
                    toNotOptionalString(fields[12]),
                    toOptionalString(fields[13]),
                    toOptionalString(fields[14]),
                    toOptionalString(fields[15])
                )
            }.toList()
    }

    private fun toNotOptionalString(value: String) = value.trim().removeSurrounding("\"")
    private fun toOptionalString(value: String): String? = value.trim().removeSurrounding("\"").ifEmpty { null }

}

data class SolscanExportedTransaction(
    val type: String,
    val hash: String,
    val blockTime: Long,
    val fee: String,
    val tokenAccountAddress: String?,
    val changeType: String?,
    val splBalanceChange: String?,
    val preBalance: String?,
    val postBalance: String?,
    val mintAccountAddress: String?,
    val tokenName: String,
    val tokenSymbol: String,
    val solTransferSource: String?,
    val solTransferDestination: String?,
    val solAmount: String?
)
