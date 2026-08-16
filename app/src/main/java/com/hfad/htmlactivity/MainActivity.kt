package com.hfad.htmlactivity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val app get() = application as HtmlActivityApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val navView = findViewById<NavigationView>(R.id.nav_view)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)

        lifecycleScope.launch {
            app.sessionManager.restore()

            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            val startDest =
                if (app.sessionManager.isLoggedIn()) R.id.chatFragment else R.id.loginFragment
            graph.setStartDestination(startDest)
            navController.graph = graph

            val appBarConfiguration = AppBarConfiguration.Builder(
                setOf(R.id.chatFragment)
            ).setOpenableLayout(drawer).build()

            toolbar.setupWithNavController(navController, appBarConfiguration)

            // 登录/注册页隐藏 toolbar 并锁住 drawer
            navController.addOnDestinationChangedListener { _, destination, _ ->
                val isAuth = destination.id == R.id.loginFragment ||
                    destination.id == R.id.registerFragment
                toolbar.visibility = if (isAuth) View.GONE else View.VISIBLE
                drawer.setDrawerLockMode(
                    if (isAuth) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                    else DrawerLayout.LOCK_MODE_UNLOCKED
                )
            }

            navView.setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.menu_logout -> {
                        lifecycleScope.launch { app.sessionManager.clear() }
                        drawer.closeDrawers()
                        navController.navigate(
                            R.id.loginFragment,
                            null,
                            NavOptions.Builder().setPopUpTo(0, true).build()
                        )
                        true
                    }
                    else -> {
                        val handled = NavigationUI.onNavDestinationSelected(item, navController)
                        if (handled) {
                            drawer.closeDrawers()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }
    }
}
