package io.horizontalsystems.solanakit.sample.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.solanakit.sample.databinding.FragmentTransactionsBinding

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewModel = ViewModelProvider(this).get(TransactionsViewModel::class.java)

        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val txsView: TextView = binding.transactions
        viewModel.transactions.observe(viewLifecycleOwner) {
            txsView.text = """
Total: ${it.size} transactions

${it.joinToString("\n\n")}
            """.trimIndent()
        }

        val noType = binding.noType
        val incomingType = binding.incoming

        val allTransactionsButton: Button = binding.allTransactions
        allTransactionsButton.setOnClickListener {
            viewModel.getAllTransactions(if (noType.isChecked) null else incomingType.isChecked)
        }

        val solTransactionsButton: Button = binding.solTransactions
        solTransactionsButton.setOnClickListener {
            viewModel.getSolTransactions(if (noType.isChecked) null else incomingType.isChecked)
        }

        val splTransactionsButton: Button = binding.splTransactions
        splTransactionsButton.setOnClickListener {
            viewModel.getSplTransactions(if (noType.isChecked) null else incomingType.isChecked)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
