package psiolent.caliper

import android.app.Application
import android.content.Context
import psiolent.caliper.agent.AgentRunner
import psiolent.caliper.data.CaliperDatabase
import psiolent.caliper.data.CaliperRepository
import psiolent.caliper.data.SettingsStore

/** App-scoped wiring. One database, one repository, one agent. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val repository = CaliperRepository(CaliperDatabase.open(context))
    val agent = AgentRunner(repository, settings)
}

class CaliperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
