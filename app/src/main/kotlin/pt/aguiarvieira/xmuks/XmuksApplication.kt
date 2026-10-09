package pt.aguiarvieira.xmuks

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import pt.aguiarvieira.xmuks.core.data.connection.ForegroundConnection
import pt.aguiarvieira.xmuks.core.data.contacts.ContactsSync
import pt.aguiarvieira.xmuks.core.data.outbox.Outbox
import pt.aguiarvieira.xmuks.core.data.push.OpenRoom
import pt.aguiarvieira.xmuks.core.notify.RoomNotifier
import javax.inject.Inject

@HiltAndroidApp
class XmuksApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var connection: ForegroundConnection

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var outbox: Outbox

    @Inject lateinit var openRoom: OpenRoom

    @Inject lateinit var notifier: RoomNotifier

    @Inject lateinit var contactsSync: ContactsSync

    /** Every Coil image in the app loads through gomuks' authenticated client. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        connection.install()
        // DM partners in the phone's Contacts, when that's turned on.
        contactsSync.start()
        // Messages left unsent by a previous run go out as soon as the app is up.
        outbox.start()
        // Opening a room is reading it: its notification goes.
        openRoom.onOpened = notifier::clear
        // Push renewal and catch-up while the app isn't open.
        CatchUpWorker.schedule(this)
    }
}
