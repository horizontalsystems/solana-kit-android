package io.horizontalsystems.solanakit.sample.ui.balance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.solanakit.sample.databinding.FragmentBalanceBinding

class BalanceFragment : Fragment() {

    private var _binding: FragmentBalanceBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val balanceViewModel = ViewModelProvider(this).get(BalanceViewModel::class.java)

        _binding = FragmentBalanceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        balanceViewModel.receiveAddress.observe(viewLifecycleOwner) {
            textView.text = it
        }

        val balanceView: TextView = binding.balanceText
        balanceViewModel.balance.observe(viewLifecycleOwner) {
            balanceView.text = it
        }

        val balanceSyncStateView: TextView = binding.balanceSyncStateText
        balanceViewModel.balanceSyncState.observe(viewLifecycleOwner) {
            balanceSyncStateView.text = it
        }

        val tokenBalanceSyncStateView: TextView = binding.tokenBalanceSyncStateText
        balanceViewModel.tokenBalanceSyncState.observe(viewLifecycleOwner) {
            tokenBalanceSyncStateView.text = it
        }

        val transactionSyncStateView: TextView = binding.txsSyncStateText
        balanceViewModel.transactionsSyncState.observe(viewLifecycleOwner) {
            transactionSyncStateView.text = it
        }

        val lastBlockHeightView: TextView = binding.lastBlockHeight
        balanceViewModel.lastBlockHeight.observe(viewLifecycleOwner) {
            lastBlockHeightView.text = it
        }

        val startButton: Button = binding.startButton
        startButton.setOnClickListener {
            balanceViewModel.start()
        }

        val refreshButton: Button = binding.refreshButton
        refreshButton.setOnClickListener {
            balanceViewModel.refresh()
        }

        val stopButton: Button = binding.stopButton
        stopButton.setOnClickListener {
            balanceViewModel.stop()
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}