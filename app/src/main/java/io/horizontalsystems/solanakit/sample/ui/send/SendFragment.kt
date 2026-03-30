package io.horizontalsystems.solanakit.sample.ui.send

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.solanakit.models.FullTokenAccount
import io.horizontalsystems.solanakit.sample.databinding.FragmentSendBinding

class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SendViewModel
    private var tokenAccounts: List<FullTokenAccount> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(SendViewModel::class.java)
        _binding = FragmentSendBinding.inflate(inflater, container, false)

        binding.tokenTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.splSection.visibility =
                if (checkedId == binding.radioSpl.id) View.VISIBLE else View.GONE
        }

        viewModel.tokenAccounts.observe(viewLifecycleOwner) { accounts ->
            tokenAccounts = accounts
            val labels = accounts.map { it.mintAccount.symbol ?: it.tokenAccount.mintAddress }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.tokenSpinner.adapter = adapter
        }

        viewModel.isSending.observe(viewLifecycleOwner) { sending ->
            binding.progressBar.visibility = if (sending) View.VISIBLE else View.GONE
            binding.sendButton.isEnabled = sending != true
        }

        viewModel.sendResult.observe(viewLifecycleOwner) { result ->
            binding.statusText.text = result
            binding.statusText.setTextColor(
                if (result.startsWith("Error")) Color.RED else Color.parseColor("#2E7D32")
            )
        }

        binding.sendButton.setOnClickListener {
            val address = binding.addressInput.text.toString().trim()
            val amount = binding.amountInput.text.toString().trim()

            if (address.isEmpty() || amount.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.radioSol.isChecked) {
                viewModel.sendSol(address, amount)
            } else {
                val selectedIndex = binding.tokenSpinner.selectedItemPosition
                if (tokenAccounts.isEmpty() || selectedIndex < 0) {
                    Toast.makeText(requireContext(), "No SPL token selected", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val selected = tokenAccounts[selectedIndex]
                viewModel.sendSpl(
                    mintAddress = selected.tokenAccount.mintAddress,
                    toAddress = address,
                    amountStr = amount,
                    decimals = selected.tokenAccount.decimals
                )
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
