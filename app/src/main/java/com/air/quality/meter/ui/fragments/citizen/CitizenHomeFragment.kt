package com.air.quality.meter.ui.fragments.citizen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.air.quality.meter.R
import com.air.quality.meter.databinding.FragmentCitizenHomeBinding

/**
 * CitizenHomeFragment — bottom navigation host for all citizen tabs.
 *
 * Tabs:
 *  1. Dashboard    — DashboardFragment
 *  2. Manual Entry — ManualEntryFragment
 *  3. History      — HistoryFragment
 *  4. Health Tips  — HealthTipsFragment
 *  5. Profile      — CitizenProfileFragment
 */
class CitizenHomeFragment : Fragment() {
    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab_id"
    }

    private var _binding: FragmentCitizenHomeBinding? = null
    private val binding get() = _binding!!

    // Keep track of which fragment is currently shown
    private var activeTab: Fragment? = null
    private var activeTabId: Int = R.id.nav_dashboard

    private var suppressNavListener = false

    private val tabFragments by lazy {
        mapOf(
            R.id.nav_dashboard to DashboardFragment(),
            R.id.nav_manual    to ManualEntryFragment(),
            R.id.nav_history   to HistoryFragment(),
            R.id.nav_health    to HealthTipsFragment(),
            R.id.nav_profile   to CitizenProfileFragment()
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCitizenHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-add all fragments, show only Dashboard initially
        tabFragments.forEach { (id, frag) ->
            if (!frag.isAdded) {
                childFragmentManager.beginTransaction()
                    .add(R.id.citizen_fragment_container, frag, id.toString())
                    .hide(frag)
                    .commit()
            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavListener) return@setOnItemSelectedListener false
            showTab(item.itemId)
            true
        }

        // Restore previously selected tab when returning from nested pages.
        val restoredTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB)
        val currentSelected = binding.bottomNav.selectedItemId
        val initialTabId = when {
            restoredTabId != null && tabFragments.containsKey(restoredTabId) -> restoredTabId
            tabFragments.containsKey(currentSelected) -> currentSelected
            else -> R.id.nav_dashboard
        }

        suppressNavListener = true
        if (binding.bottomNav.selectedItemId != initialTabId) {
            binding.bottomNav.selectedItemId = initialTabId
        }
        suppressNavListener = false
        showTab(initialTabId)
    }

    private fun showTab(tabId: Int) {
        val target = tabFragments[tabId] ?: return
        val tx = childFragmentManager.beginTransaction()
        activeTab?.let { tx.hide(it) }
        tx.show(target)
        tx.commit()
        activeTab = target
        activeTabId = tabId
        // Do not set binding.bottomNav.selectedItemId here — selection should be driven by user clicks
        // or by calling selectedItemId with suppressNavListener = true to avoid recursion.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, activeTabId)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
