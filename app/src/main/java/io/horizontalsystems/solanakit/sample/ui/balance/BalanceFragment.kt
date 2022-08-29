package io.horizontalsystems.solanakit.sample.ui.balance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.solanakit.sample.databinding.FragmentHomeBinding

class BalanceFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val balanceViewModel = ViewModelProvider(this).get(BalanceViewModel::class.java)
        balanceViewModel.init()

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        balanceViewModel.receiveAddress.observe(viewLifecycleOwner) {
            textView.text = it
        }

        val balanceView: TextView = binding.balanceText
        balanceViewModel.balance.observe(viewLifecycleOwner) {
            balanceView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}