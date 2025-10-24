package it.reyboz.bustorino

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import it.reyboz.bustorino.fragments.BackupImportFragment
import it.reyboz.bustorino.middleware.GeneralActivity

class ActivityBackup : GeneralActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container_fragment)

        val mToolbar = findViewById<Toolbar>(R.id.default_toolbar)
        setSupportActionBar(mToolbar)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            //actionBar.setIcon(R.drawable.ic_launcher)
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container_view, BackupImportFragment::class.java, null)
                .commit()
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById<View>(R.id.fragment_container_view),
            this.applyBottomAndBordersInsetsListener
        )

        ViewCompat.setOnApplyWindowInsetsListener(mToolbar) { v: View?, windowInsets: WindowInsetsCompat? ->
            val insets = windowInsets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            v?.updatePadding(top=insets.top)
            windowInsets
        }
    }

}