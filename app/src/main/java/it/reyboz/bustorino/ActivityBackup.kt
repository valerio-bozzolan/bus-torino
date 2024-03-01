package it.reyboz.bustorino

import android.os.Bundle
import it.reyboz.bustorino.fragments.BackupImportFragment
import it.reyboz.bustorino.middleware.GeneralActivity

class ActivityBackup : GeneralActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container_fragment)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setIcon(R.drawable.ic_launcher)
            actionBar.show()
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container_view, BackupImportFragment::class.java, null)
                .commit()
        }
    }

}