package pt.aguiarvieira.xmuks.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import pt.aguiarvieira.xmuks.core.data.links.LinkResolver
import pt.aguiarvieira.xmuks.core.data.links.LinkTarget
import javax.inject.Inject

/** Resolves Matrix links for the navigation: from other apps, notifications, or tapped in messages. */
@HiltViewModel
class LinkViewModel
    @Inject
    constructor(
        private val resolver: LinkResolver,
    ) : ViewModel() {
        suspend fun resolve(uri: String): LinkTarget = resolver.resolve(uri)
    }
