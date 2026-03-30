package com.air.quality.meter.ui.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.air.quality.meter.R
import com.air.quality.meter.databinding.FragmentAdminHomeBinding
import androidx.navigation.fragment.findNavController

/**
 * AdminHomeFragment — bottom navigation host for all admin tabs.
 *
 * Tabs:
 *  1. Users          — AdminUsersFragment       (UC08)
 *  2. Datasets       — AdminDatasetsFragment     (UC09)
 *  3. Thresholds     — AdminThresholdsFragment   (UC10)
 *  4. Health Tips    — AdminRecommendationsFragment (UC11)
 *  5. Activity Log   — AdminActivityFragment     (UC12)
 */
class AdminHomeFragment : Fragment() {

    private var _binding: FragmentAdminHomeBinding? = null
    private val binding get() = _binding!!

    private var activeTab: Fragment? = null
    private var suppressNavListener = false

    private val tabFragments by lazy {
        mapOf(
            R.id.admin_nav_users           to AdminUsersFragment(),
            R.id.admin_nav_datasets        to AdminDatasetsFragment(),
            R.id.admin_nav_thresholds      to AdminThresholdsFragment(),
            R.id.admin_nav_recommendations to AdminRecommendationsFragment(),
            R.id.admin_nav_logs            to AdminActivityFragment()
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabFragments.forEach { (id, frag) ->
            if (!frag.isAdded) {
                childFragmentManager.beginTransaction()
                    .add(R.id.admin_fragment_container, frag, id.toString())
                    .hide(frag)
                    .commit()
            }
        }

        // Set initial selection without triggering listener
        suppressNavListener = true
        binding.adminBottomNav.setOnItemSelectedListener { item ->
            if (suppressNavListener) return@setOnItemSelectedListener false
            showTab(item.itemId)
            true
        }

        suppressNavListener = false
        showTab(R.id.admin_nav_users)
    }

    // Toolbar removed per user preference

    private fun showTab(tabId: Int) {
        val target = tabFragments[tabId] ?: return
        val tx = childFragmentManager.beginTransaction()
        activeTab?.let { tx.hide(it) }
        tx.show(target)
        tx.commit()
        activeTab = target
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
