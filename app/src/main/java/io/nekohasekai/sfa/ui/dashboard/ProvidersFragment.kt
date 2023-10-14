package io.nekohasekai.sfa.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundProvider
import io.nekohasekai.libbox.OutboundProviderItem
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.databinding.FragmentDashboardProvidersBinding
import io.nekohasekai.sfa.databinding.ViewDashboardProviderBinding
import io.nekohasekai.sfa.databinding.ViewDashboardProviderItemBinding
import io.nekohasekai.sfa.ktx.colorForURLTestDelay
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ProvidersFragment : Fragment(), CommandClient.Handler {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var binding: FragmentDashboardProvidersBinding? = null
    private var adapter: Adapter? = null
    private val commandClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Providers, this)


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDashboardProvidersBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return
        adapter = Adapter()
        binding.container.adapter = adapter
        binding.container.layoutManager = LinearLayoutManager(requireContext())
        activity.serviceStatus.observe(viewLifecycleOwner) {
            if (it == Status.Started) {
                commandClient.connect()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private var displayed = false
    private fun updateDisplayed(newValue: Boolean) {
        val binding = binding ?: return
        if (displayed != newValue) {
            displayed = newValue
            binding.statusText.isVisible = !displayed
            binding.container.isVisible = displayed
        }
    }

    override fun onConnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateDisplayed(true)
        }
    }

    override fun onDisconnected() {
        lifecycleScope.launch(Dispatchers.Main) {
            updateDisplayed(false)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun updateProviders(providers: List<OutboundProvider>) {
        val adapter = adapter ?: return
        activity?.runOnUiThread {
            updateDisplayed(providers.isNotEmpty())
            adapter.providers = providers
            adapter.notifyDataSetChanged()
        }
    }

    private class Adapter : RecyclerView.Adapter<ProviderView>() {

        lateinit var providers: List<OutboundProvider>
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProviderView {
            return ProviderView(
                ViewDashboardProviderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
            )
        }

        override fun getItemCount(): Int {
            if (!::providers.isInitialized) {
                return 0
            }
            return providers.size
        }

        override fun onBindViewHolder(holder: ProviderView, position: Int) {
            holder.bind(providers[position])
        }
    }

    private class ProviderView(val binding: ViewDashboardProviderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        lateinit var provider: OutboundProvider
        lateinit var items: MutableList<OutboundProviderItem>
        lateinit var adapter: ItemAdapter
        fun bind(provider: OutboundProvider) {
            this.provider = provider
            binding.providerName.text = provider.tag
            binding.providerType.text = Libbox.providerDisplayType(provider.type)
            binding.urlTestButton.setOnClickListener {
                GlobalScope.launch {
                    runCatching {
                        Libbox.newStandaloneCommandClient().healthCheck(provider.tag)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
            items = mutableListOf()
            val itemIterator = provider.items
            while (itemIterator.hasNext()) {
                items.add(itemIterator.next())
            }
            adapter = ItemAdapter(this, provider, items)
            binding.itemList.adapter = adapter
            (binding.itemList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            binding.itemList.layoutManager = GridLayoutManager(binding.root.context, 2)
            updateExpand()
        }

        private fun updateExpand(isExpand: Boolean? = null) {
            val newExpandStatus = isExpand ?: provider.isExpand
            if (isExpand != null) {
                GlobalScope.launch {
                    runCatching {
                        Libbox.newStandaloneCommandClient().setProviderExpand(provider.tag, isExpand)
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
            binding.itemList.isVisible = newExpandStatus
            binding.itemText.isVisible = !newExpandStatus
            if (!newExpandStatus) {
                val builder = SpannableStringBuilder()
                items.forEach {
                    builder.append("■")
                    builder.setSpan(
                        ForegroundColorSpan(
                            colorForURLTestDelay(
                                binding.root.context,
                                it.urlTestDelay
                            )
                        ),
                        builder.length - 1,
                        builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append(" ")
                }
                binding.itemText.text = builder
            }
            if (newExpandStatus) {
                binding.expandButton.setImageResource(R.drawable.ic_expand_less_24)
            } else {
                binding.expandButton.setImageResource(R.drawable.ic_expand_more_24)
            }
            binding.expandButton.setOnClickListener {
                updateExpand(!binding.itemList.isVisible)
            }
        }
    }

    private class ItemAdapter(
        val providerView: ProviderView,
        val provider: OutboundProvider,
        val items: List<OutboundProviderItem>
    ) :
        RecyclerView.Adapter<ItemProviderView>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemProviderView {
            return ItemProviderView(
                ViewDashboardProviderItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ItemProviderView, position: Int) {
            holder.bind(providerView, provider, items[position])
        }
    }

    private class ItemProviderView(val binding: ViewDashboardProviderItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(providerView: ProviderView, provider: OutboundProvider, item: OutboundProviderItem) {
            binding.itemName.text = item.tag
            binding.itemType.text = Libbox.providerDisplayType(item.type)
            binding.itemStatus.isVisible = item.urlTestTime > 0
            if (item.urlTestTime > 0) {
                binding.itemStatus.text = "${item.urlTestDelay}ms"
                binding.itemStatus.setTextColor(
                    colorForURLTestDelay(
                        binding.root.context,
                        item.urlTestDelay
                    )
                )
            }
        }
    }

}

