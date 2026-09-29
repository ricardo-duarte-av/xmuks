package pt.aguiarvieira.xmuks.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.aguiarvieira.xmuks.core.data.roominfo.Membership
import pt.aguiarvieira.xmuks.core.data.roominfo.PowerLevels
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomInfo
import pt.aguiarvieira.xmuks.core.data.roominfo.RoomMember

class MemberGroupsTest {
    private fun member(
        id: String,
        level: Long,
        membership: Membership = Membership.Join,
    ) = RoomMember("@$id:x", null, null, membership, level, null)

    private val info =
        RoomInfo(
            roomId = "!r",
            name = null,
            topic = null,
            avatarMxc = null,
            canonicalAlias = null,
            altAliases = emptyList(),
            encrypted = false,
            joinRule = "knock",
            historyVisibility = "shared",
            guestAccess = false,
            roomVersion = "12",
            isSpace = false,
            replacementRoom = null,
            powerLevels = PowerLevels(emptyMap(), 0, emptyMap(), 0, 50, 50, 50, 0, 50),
            members =
                listOf(
                    member("creator", PowerLevels.CREATOR),
                    member("admin", 100),
                    member("mod", 50),
                    member("odd", 20),
                    member("user", 0),
                    member("user2", 0),
                    member("knocker", 0, Membership.Knock),
                    member("invitee", 0, Membership.Invite),
                    member("banned", 0, Membership.Ban),
                    member("gone", 0, Membership.Leave),
                ),
        )

    @Test
    fun `knocking first, then levels high to low, then invited, the gone left out`() {
        val groups = groupMembers(info, "")
        assertEquals(
            listOf("Knocking", "level-${PowerLevels.CREATOR}", "level-100", "level-50", "level-20", "level-0", "Invited"),
            groups.map { it.key },
        )
        assertEquals(2, groups.first { it.level == 0L }.members.size)
        assertEquals(emptyList<String>(), groups.flatMap { g -> g.members.map { it.userId } }.filter { "banned" in it || "gone" in it })
    }

    @Test
    fun `search narrows every group and drops empty ones`() {
        assertEquals(listOf("level-0"), groupMembers(info, "USER").map { it.key })
    }
}
