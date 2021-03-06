package org.tokend.template.features.wallet

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.view.GestureDetectorCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.collapsing_balance_appbar.*
import kotlinx.android.synthetic.main.fragment_wallet.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import org.jetbrains.anko.dip
import org.jetbrains.anko.onClick
import org.tokend.template.BuildConfig
import org.tokend.template.R
import org.tokend.template.data.model.BalanceRecord
import org.tokend.template.data.repository.balancechanges.BalanceChangesRepository
import org.tokend.template.data.repository.balances.BalancesRepository
import org.tokend.template.features.wallet.adapter.BalanceChangeListItem
import org.tokend.template.features.wallet.adapter.BalanceChangesAdapter
import org.tokend.template.fragments.BaseFragment
import org.tokend.template.fragments.ToolbarProvider
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.view.util.HorizontalSwipesGestureDetector
import org.tokend.template.view.util.LoadingIndicatorManager
import java.lang.ref.WeakReference
import java.util.*

class WalletFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private lateinit var balancesRepository: BalancesRepository
    private val balanceChangesRepository: BalanceChangesRepository
        get() = repositoryProvider.balanceChanges(balanceId)

    private val needAssetTabs: Boolean
        get() = arguments?.getBoolean(NEED_TABS_EXTRA) == true

    private var asset: String = ""
        set(value) {
            field = value
            onAssetChanged()
        }

    private val balanceId: String
        get() = balancesRepository.itemsList.find { it.assetCode == asset }?.id ?: ""

    private lateinit var adapter: BalanceChangesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        balancesRepository = repositoryProvider.balances()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onInitAllowed() {
        toolbarSubject.onNext(toolbar)

        initAssetTabs()
        initHistory()
        initSwipeRefresh()
        initSend()
        initHorizontalSwipesIfNeeded()

        arguments
                ?.getString(ASSET_EXTRA)
                ?.also { requiredAsset ->
                    asset = requiredAsset
                }

        subscribeToBalances()
    }

    private fun initSend() {
        send_fab.onClick {
            Navigator.openSend(this, asset, SEND_REQUEST)
        }
    }

    // region Init
    private fun initAssetTabs() {
        asset_tabs.onItemSelected {
            asset = it.text
        }

        if (!needAssetTabs) {
            asset_tabs.visibility = View.GONE
        } else {
            asset_tabs.visibility = View.VISIBLE

            val tabsOffset = requireContext().dip(24)
            collapsing_toolbar.layoutParams =
                    collapsing_toolbar.layoutParams.apply {
                        height -= 2 * tabsOffset
                    }
        }
    }

    private val hideFabScrollListener =
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 2) {
                        send_fab.hide()
                    } else if (dy < -2 && send_fab.isEnabled) {
                        send_fab.show()
                    }
                }
            }

    private fun initHistory() {
        adapter = BalanceChangesAdapter(amountFormatter, false)
        adapter.onItemClick { _, item ->
            item.source?.let { Navigator.openBalanceChangeDetails(this.requireActivity(), it) }
        }

        error_empty_view.setEmptyDrawable(R.drawable.ic_balance)
        error_empty_view.setPadding(0, 0, 0,
                resources.getDimensionPixelSize(R.dimen.quadra_margin))
        error_empty_view.observeAdapter(adapter, R.string.no_transaction_history)
        error_empty_view.setEmptyViewDenial { balanceChangesRepository.isNeverUpdated }

        history_list.adapter = adapter
        history_list.layoutManager = LinearLayoutManager(context!!)
        history_list.addOnScrollListener(hideFabScrollListener)

        history_list.listenBottomReach({ adapter.getDataItemCount() }) {
            balanceChangesRepository.loadMore() || balanceChangesRepository.noMoreItems
        }
    }

    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initHorizontalSwipesIfNeeded() {
        if (!needAssetTabs) {
            return
        }

        val weakTabs = WeakReference(asset_tabs)

        val gestureDetector = GestureDetectorCompat(requireContext(), HorizontalSwipesGestureDetector(
                onSwipeToLeft = {
                    weakTabs.get()?.apply { selectedItemIndex++ }
                },
                onSwipeToRight = {
                    weakTabs.get()?.apply { selectedItemIndex-- }
                }
        ))

        touch_capture_layout.setTouchEventInterceptor(gestureDetector::onTouchEvent)
        swipe_refresh.setOnTouchListener { _, event ->
            if (error_empty_view.visibility == View.VISIBLE)
                gestureDetector.onTouchEvent(event)

            false
        }
    }
    // endregion

    // region Subscriptions
    private fun subscribeToBalances() {
        balancesRepository.itemsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    onBalancesUpdated(it)
                }
                .addTo(compositeDisposable)
        balancesRepository.loadingSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    loadingIndicator.setLoading(it, "balances")
                }
                .addTo(compositeDisposable)
        balancesRepository.errorsSubject
                .compose(ObservableTransformers.defaultSchedulers())
                .subscribe {
                    errorHandlerFactory.getDefault().handle(it)
                }
                .addTo(compositeDisposable)
    }

    private var historyDisposable: Disposable? = null
    private fun subscribeToHistory() {
        historyDisposable?.dispose()

        val accountId = walletInfoProvider.getWalletInfo()?.accountId
                ?: return

        historyDisposable = CompositeDisposable(
                balanceChangesRepository.itemsSubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            adapter.setData(it.map { balanceChange ->
                                BalanceChangeListItem(balanceChange, accountId)
                            })
                            history_list.resetBottomReachHandled()
                        },
                balanceChangesRepository.loadingSubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { loading ->
                            if (loading) {
                                if (balanceChangesRepository.isOnFirstPage) {
                                    loadingIndicator.show("transactions")
                                } else {
                                    adapter.showLoadingFooter()
                                }
                            } else {
                                loadingIndicator.hide("transactions")
                                adapter.hideLoadingFooter()
                            }
                        },
                balanceChangesRepository.errorsSubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { error ->
                            if (!adapter.hasData) {
                                error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                                    update(true)
                                }
                            } else {
                                errorHandlerFactory.getDefault().handle(error)
                            }
                        }
        )
                .addTo(compositeDisposable)
    }
    // endregion

    // region Display
    private fun displayAssetTabs(assets: List<String>) {
        Collections.sort(assets, assetComparator)
        asset_tabs.setSimpleItems(assets, asset)
    }

    private fun displayBalance() {
        balancesRepository.itemsList
                .find { it.assetCode == asset }
                ?.let { balance ->
                    collapsing_toolbar.title =
                            amountFormatter.formatAssetAmount(balance.available, asset)
                }
    }

    private fun displaySendIfNeeded() {
        (balancesRepository.itemsList
                .find { it.assetCode == asset }
                ?.asset
                ?.isTransferable == true)
                .let { isTransferable ->
                    if (!isTransferable || !BuildConfig.IS_SEND_ALLOWED) {
                        send_fab.hide()
                        send_fab.isEnabled = false
                    } else {
                        send_fab.show()
                        send_fab.isEnabled = true
                    }
                }
    }
    // endregion

    private fun update(force: Boolean = false) {
        if (!force) {
            balancesRepository.updateIfNotFresh()
            balanceChangesRepository.updateIfNotFresh()
        } else {
            balancesRepository.update()
            balanceChangesRepository.update()
        }
    }

    private fun onBalancesUpdated(balances: List<BalanceRecord>) {
        displayAssetTabs(balances.map { it.assetCode })
        displayBalance()
        displaySendIfNeeded()
    }

    private fun onAssetChanged() {
        displayBalance()
        subscribeToHistory()
        date_text_switcher.init(history_list, adapter)
        history_list.scrollToPosition(0)
        displaySendIfNeeded()
        update()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        history_list.clearOnScrollListeners()
    }

    companion object {
        private const val ASSET_EXTRA = "asset"
        private const val NEED_TABS_EXTRA = "need_tabs"
        private val SEND_REQUEST = "send".hashCode() and 0xffff
        const val ID = 1111L

        fun newInstance(asset: String? = null, needTabs: Boolean = true): WalletFragment {
            val fragment = WalletFragment()
            fragment.arguments = Bundle().apply {
                putString(ASSET_EXTRA, asset)
                putBoolean(NEED_TABS_EXTRA, needTabs)
            }
            return fragment
        }
    }
}
