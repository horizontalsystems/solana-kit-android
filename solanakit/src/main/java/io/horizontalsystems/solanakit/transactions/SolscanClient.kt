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
                }?:run {
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
                    fields[0].trim().removeSurrounding("\""),
                    fields[1].trim().removeSurrounding("\""),
                    fields[2].trim().removeSurrounding("\""),
                    fields[4].trim().removeSurrounding("\""),
                    fields[5].trim().removeSurrounding("\""),
                    fields[6].trim().removeSurrounding("\""),
                    fields[7].trim().removeSurrounding("\""),
                    fields[8].trim().removeSurrounding("\""),
                    fields[9].trim().removeSurrounding("\""),
                    fields[10].trim().removeSurrounding("\""),
                    fields[11].trim().removeSurrounding("\""),
                    fields[12].trim().removeSurrounding("\""),
                    fields[13].trim().removeSurrounding("\""),
                    fields[14].trim().removeSurrounding("\""),
                    fields[15].trim().removeSurrounding("\""),
                )
            }.toList()
    }

}

data class SolscanExportedTransaction(
    val type: String,
    val hash: String,
    val blockTime: String,
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
