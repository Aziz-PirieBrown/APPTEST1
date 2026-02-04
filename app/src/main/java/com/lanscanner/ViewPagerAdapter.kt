package com.lanscanner

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    val configFragment = ConfigFragment()
    val logFragment = LogFragment()
    
    override fun getItemCount(): Int = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> configFragment
            1 -> logFragment
            else -> configFragment
        }
    }
}
