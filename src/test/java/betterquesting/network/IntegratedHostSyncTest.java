package betterquesting.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.utils.NBTConverter;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetLifeSync;
import betterquesting.network.handlers.NetNameSync;
import betterquesting.network.handlers.NetPartyAction;
import betterquesting.network.handlers.NetPartySync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.LifeDatabase;
import betterquesting.storage.NameCache;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalChannel;

/**
 * On the client that hosts the integrated server (singleplayer / Open to LAN), the client and the server share the
 * same database singletons. These tests feed the client packet handlers the payloads the server sends and check that:
 * <ul>
 * <li>a packet is classified as "from the host's own server" only when it arrived over the in-memory connection;</li>
 * <li>on the host, the handlers leave the server's objects alone (no clear, no reset, no stale overwrite);</li>
 * <li>on a remote client, the handlers still apply the data exactly as before.</li>
 * </ul>
 * Quests, chapters and parties are recording stand-ins: constructing the real ones needs a fully bootstrapped game
 * (their default icon pulls in the ore dictionary and vanilla recipes). The life and name databases are real.
 */
class IntegratedHostSyncTest {

    private static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID QUEST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID CHAPTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final int PARTY_ID = 7;

    @BeforeEach
    void setUp() {
        resetDatabases();
    }

    @AfterEach
    void tearDown() {
        resetDatabases();
    }

    // --- Packet classification --------------------------------------------------------------------------------------

    /** Only the integrated server connection is in-memory; LAN friends and dedicated servers use TCP. */
    @Test
    void onlyInMemoryConnectionsCountAsHost() throws Exception {
        assertTrue(IntegratedServerHost.isHostConnection(connection(new LocalChannel())));
        EmbeddedChannel remote = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            assertFalse(IntegratedServerHost.isHostConnection(connection(remote)));
        } finally {
            remote.close();
        }
        assertFalse(IntegratedServerHost.isHostConnection(null));
    }

    /**
     * Outside a packet handler nothing is treated as host data. In particular a stale
     * {@code Minecraft.isIntegratedServerRunning()} (left set by an aborted singleplayer launch) cannot make a remote
     * server's packets be ignored, because the decision is made per packet.
     */
    @Test
    void hostFlagOnlyHoldsWhileHandlingAHostPacket() {
        assertFalse(IntegratedServerHost.isHost());
        IntegratedServerHost.runHandler(true, () -> assertTrue(IntegratedServerHost.isHost()));
        IntegratedServerHost.runHandler(false, () -> assertFalse(IntegratedServerHost.isHost()));
        assertFalse(IntegratedServerHost.isHost());

        assertThrows(
            IllegalStateException.class,
            () -> IntegratedServerHost.runHandler(true, () -> { throw new IllegalStateException("handler failure"); }));
        assertFalse(IntegratedServerHost.isHost(), "flag leaked after a handler threw");
    }

    // --- Quest sync -------------------------------------------------------------------------------------------------

    /**
     * A full config sync (quest import, /bq_admin default load, quest delete command) is broadcast to everyone,
     * including the host's own client. Clearing the shared database there wipes every other player's progress.
     */
    @Test
    void hostKeepsServerQuestsOnFullConfigSync() throws Exception {
        Recorder quest = new Recorder();
        QuestDatabase.INSTANCE.put(QUEST_ID, quest.as(IQuest.class));

        invokeAs(true, NetQuestSync.class, fullSyncPayload());

        assertSame(quest.proxy, QuestDatabase.INSTANCE.get(QUEST_ID), "server quest was cleared from the database");
    }

    /** Config updates for single quests (quest editing) rebuild the quest's tasks, dropping their progress. */
    @Test
    void hostDoesNotRewriteServerQuestOnConfigUpdate() throws Exception {
        Recorder quest = new Recorder();
        QuestDatabase.INSTANCE.put(QUEST_ID, quest.as(IQuest.class));

        invokeAs(true, NetQuestSync.class, questPayload(true, true, false, false));

        assertEquals(Collections.emptyList(), quest.calls, "host client rewrote a server quest");
    }

    /**
     * Progress packets are built on a background thread and applied later on the client thread. On the host that
     * snapshot would overwrite newer server progress.
     */
    @Test
    void hostDoesNotApplyProgressSnapshots() throws Exception {
        Recorder quest = new Recorder();
        QuestDatabase.INSTANCE.put(QUEST_ID, quest.as(IQuest.class));

        invokeAs(true, NetQuestSync.class, questPayload(false, true, true, true));

        assertEquals(Collections.emptyList(), quest.calls, "host client applied a stale progress snapshot");
    }

    @Test
    void remoteClientStillAppliesQuestConfigAndMergedProgress() throws Exception {
        Recorder quest = new Recorder();
        QuestDatabase.INSTANCE.put(QUEST_ID, quest.as(IQuest.class));

        invokeAs(false, NetQuestSync.class, questPayload(true, true, true, false));

        assertEquals(Arrays.asList("readFromNBT", "readProgressFromNBT"), quest.calls);
        assertEquals(true, quest.lastArgs[1], "remote merge flag changed");
    }

    @Test
    void remoteClientStillOverwritesProgressOnResetCompletion() throws Exception {
        Recorder quest = new Recorder();
        QuestDatabase.INSTANCE.put(QUEST_ID, quest.as(IQuest.class));

        invokeAs(false, NetQuestSync.class, questPayload(false, true, true, true));

        assertEquals(Arrays.asList("readProgressFromNBT"), quest.calls);
        assertEquals(false, quest.lastArgs[1], "remote client must replace progress when completion is reset");
    }

    @Test
    void remoteClientStillClearsQuestsOnFullSync() throws Exception {
        Recorder stale = new Recorder();
        QuestDatabase.INSTANCE.put(UUID.randomUUID(), stale.as(IQuest.class));

        invokeAs(false, NetQuestSync.class, fullSyncPayload());

        assertEquals(0, QuestDatabase.INSTANCE.size());
    }

    // --- Chapter sync -----------------------------------------------------------------------------------------------

    /** Full chapter syncs are broadcast together with full quest syncs (import, default load). */
    @Test
    void hostKeepsServerChaptersOnFullChapterSync() throws Exception {
        Recorder chapter = new Recorder();
        QuestLineDatabase.INSTANCE.put(CHAPTER_ID, chapter.as(IQuestLine.class));

        invokeAs(true, NetChapterSync.class, fullSyncPayload());

        assertSame(chapter.proxy, QuestLineDatabase.INSTANCE.get(CHAPTER_ID), "server chapter was cleared");
    }

    @Test
    void remoteClientStillClearsChaptersOnFullChapterSync() throws Exception {
        Recorder chapter = new Recorder();
        QuestLineDatabase.INSTANCE.put(CHAPTER_ID, chapter.as(IQuestLine.class));

        invokeAs(false, NetChapterSync.class, fullSyncPayload());

        assertNull(QuestLineDatabase.INSTANCE.get(CHAPTER_ID));
    }

    // --- Party sync / party actions ---------------------------------------------------------------------------------

    /**
     * Accepting a party invite sends the updated party (a merge sync) to every online member, including the host. On
     * the host that snapshot would overwrite the server's party, e.g. dropping a member who joined in between.
     */
    @Test
    void hostDoesNotOverwriteServerPartyWithSnapshot() throws Exception {
        Recorder party = new Recorder();
        PartyManager.INSTANCE.add(PARTY_ID, party.as(IParty.class));

        invokeAs(true, NetPartySync.class, partyPayload(true));

        assertEquals(Collections.emptyList(), party.calls, "host client overwrote a server party");
    }

    /** Defensive: a non-merge party sync (sent without party IDs) would reset the host's whole party list. */
    @Test
    void hostKeepsServerPartiesOnFullPartySync() throws Exception {
        Recorder party = new Recorder();
        Recorder otherParty = new Recorder();
        PartyManager.INSTANCE.add(PARTY_ID, party.as(IParty.class));
        PartyManager.INSTANCE.add(PARTY_ID + 1, otherParty.as(IParty.class));

        invokeAs(true, NetPartySync.class, fullSyncPayload());

        assertSame(party.proxy, PartyManager.INSTANCE.getValue(PARTY_ID), "server party was reset");
        assertSame(otherParty.proxy, PartyManager.INSTANCE.getValue(PARTY_ID + 1), "other parties were reset");
    }

    @Test
    void remoteClientStillAppliesPartySync() throws Exception {
        Recorder party = new Recorder();
        PartyManager.INSTANCE.add(PARTY_ID, party.as(IParty.class));

        invokeAs(false, NetPartySync.class, partyPayload(true));

        assertEquals(Arrays.asList("readFromNBT"), party.calls);
    }

    /**
     * The server frees a party ID as soon as it deletes the party, and new parties take the first free ID. If someone
     * creates a party before the host processes the delete echo, the echo would delete the new party on the server.
     */
    @Test
    void hostDeleteEchoDoesNotRemoveReplacementParty() throws Exception {
        Recorder replacement = new Recorder();
        PartyManager.INSTANCE.add(PARTY_ID, replacement.as(IParty.class)); // reused the deleted party's ID

        invokeAs(true, NetPartyAction.class, partyDeletePayload());

        assertSame(replacement.proxy, PartyManager.INSTANCE.getValue(PARTY_ID), "delete echo removed a new party");
    }

    @Test
    void remoteClientStillAppliesPartyDelete() throws Exception {
        Recorder party = new Recorder();
        PartyManager.INSTANCE.add(PARTY_ID, party.as(IParty.class));

        invokeAs(false, NetPartyAction.class, partyDeletePayload());

        assertNull(PartyManager.INSTANCE.getValue(PARTY_ID));
    }

    // --- Life sync (real database) ----------------------------------------------------------------------------------

    /** NetLifeSync.sendSync with player IDs: a merge snapshot of one player, applied a moment later. */
    @Test
    void hostDoesNotApplyStaleLifeSnapshot() throws Exception {
        seedLives(HOST, 3, FRIEND, 2);
        NBTTagCompound snapshot = lifePayload(Collections.singletonList(HOST), true);
        seedLives(HOST, 1); // lost a life after the snapshot was built

        invokeAs(true, NetLifeSync.class, snapshot);

        assertEquals(1, LifeDatabase.INSTANCE.getLives(HOST), "stale snapshot gave a lost life back");
        assertEquals(2, LifeDatabase.INSTANCE.getLives(FRIEND));
    }

    /** NetLifeSync.sendSync without player IDs: a full (non-merge) snapshot of everyone. */
    @Test
    void hostDoesNotApplyStaleFullLifeSync() throws Exception {
        seedLives(HOST, 3, FRIEND, 2);
        NBTTagCompound snapshot = lifePayload(null, false);
        seedLives(FRIEND, 1); // friend lost a life after the snapshot was built

        invokeAs(true, NetLifeSync.class, snapshot);

        assertEquals(1, LifeDatabase.INSTANCE.getLives(FRIEND), "stale full sync reset a friend's lives");
    }

    @Test
    void remoteClientStillAppliesLifeSync() throws Exception {
        seedLives(HOST, 3);
        NBTTagCompound payload = lifePayload(Collections.singletonList(HOST), true);
        seedLives(HOST, 1);

        invokeAs(false, NetLifeSync.class, payload);

        assertEquals(3, LifeDatabase.INSTANCE.getLives(HOST));
    }

    // --- Name sync (real database) ----------------------------------------------------------------------------------

    /** NetNameSync.sendNames / quickSync send merge snapshots of a few players. */
    @Test
    void hostDoesNotApplyStaleNameSnapshot() throws Exception {
        NameCache.INSTANCE.readFromNBT(names(HOST, "Host", FRIEND, "Friend"), false);
        NBTTagCompound snapshot = namePayload(names(FRIEND, "Friend"), true);
        NameCache.INSTANCE.readFromNBT(names(FRIEND, "FriendRenamed"), true); // changed after the snapshot

        invokeAs(true, NetNameSync.class, snapshot);

        assertEquals("FriendRenamed", NameCache.INSTANCE.getName(FRIEND), "stale snapshot reverted a name");
    }

    /** NetNameSync.sendNames without IDs: a full (non-merge) list, here missing a player who joined since. */
    @Test
    void hostKeepsServerNameCacheOnFullNameSync() throws Exception {
        NameCache.INSTANCE.readFromNBT(names(HOST, "Host"), false);
        NBTTagCompound snapshot = namePayload(names(HOST, "Host"), false);
        NameCache.INSTANCE.readFromNBT(names(FRIEND, "Friend"), true); // joined after the snapshot

        invokeAs(true, NetNameSync.class, snapshot);

        assertEquals("Friend", NameCache.INSTANCE.getName(FRIEND), "friend dropped from the server name cache");
    }

    @Test
    void remoteClientStillAppliesNameSync() throws Exception {
        NameCache.INSTANCE.readFromNBT(names(HOST, "Host", FRIEND, "Friend"), false);

        invokeAs(false, NetNameSync.class, namePayload(names(HOST, "Host"), false));

        assertEquals("Host", NameCache.INSTANCE.getName(HOST));
        assertFalse("Friend".equals(NameCache.INSTANCE.getName(FRIEND)));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static void resetDatabases() {
        QuestDatabase.INSTANCE.clear();
        QuestLineDatabase.INSTANCE.clear();
        PartyManager.INSTANCE.reset();
        LifeDatabase.INSTANCE.reset();
        NameCache.INSTANCE.reset();
    }

    /** A client-side NetworkManager whose connection is the given channel. */
    private static NetworkManager connection(io.netty.channel.Channel channel) throws Exception {
        NetworkManager manager = new NetworkManager(true);
        Field field = NetworkManager.class.getDeclaredField("channel");
        field.setAccessible(true);
        field.set(manager, channel);
        return manager;
    }

    /** Same layout as {@code NetQuestSync.sendSync}. */
    private static NBTTagCompound questPayload(boolean config, boolean merge, boolean progress,
        boolean resetCompletion) {
        NBTTagCompound questTag = new NBTTagCompound();
        if (config) {
            questTag.setTag("config", new NBTTagCompound());
        }
        if (progress) {
            questTag.setTag("progress", new NBTTagCompound());
        }
        NBTConverter.UuidValueType.QUEST.writeId(QUEST_ID, questTag);

        NBTTagList list = new NBTTagList();
        list.appendTag(questTag);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("data", list);
        payload.setBoolean("merge", merge);
        payload.setBoolean("resetCompletion", resetCompletion);
        return payload;
    }

    /** Full (non-merge) sync with no entries: a receiver that honours it ends up empty. */
    private static NBTTagCompound fullSyncPayload() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("data", new NBTTagList());
        payload.setBoolean("merge", false);
        return payload;
    }

    /** Same layout as {@code NetPartySync.sendSync}. */
    private static NBTTagCompound partyPayload(boolean merge) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("partyID", PARTY_ID);
        entry.setTag("config", new NBTTagCompound());
        NBTTagList list = new NBTTagList();
        list.appendTag(entry);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("data", list);
        payload.setBoolean("merge", merge);
        return payload;
    }

    /** Same layout as the delete notification in {@code NetPartyAction}. */
    private static NBTTagCompound partyDeletePayload() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("action", 1); // ACTION_DELETE
        payload.setInteger("partyID", PARTY_ID);
        return payload;
    }

    /** Same layout as {@code NetLifeSync.sendSync}: merge iff player IDs were given. */
    private static NBTTagCompound lifePayload(List<UUID> players, boolean merge) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("data", LifeDatabase.INSTANCE.writeToNBT(new NBTTagCompound(), players));
        payload.setBoolean("merge", merge);
        return payload;
    }

    /** Same layout as {@code NetNameSync}. */
    private static NBTTagCompound namePayload(NBTTagList names, boolean merge) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("data", names);
        payload.setBoolean("merge", merge);
        return payload;
    }

    /** Writes lives directly ({@code setLives} reads the quest settings, which need a bootstrapped game). */
    private static void seedLives(Object... uuidLivesPairs) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < uuidLivesPairs.length; i += 2) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", uuidLivesPairs[i].toString());
            entry.setInteger("lives", (Integer) uuidLivesPairs[i + 1]);
            list.appendTag(entry);
        }
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("playerLives", list);
        LifeDatabase.INSTANCE.readFromNBT(nbt, true);
    }

    private static NBTTagList names(Object... uuidNamePairs) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < uuidNamePairs.length; i += 2) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", uuidNamePairs[i].toString());
            entry.setString("name", (String) uuidNamePairs[i + 1]);
            list.appendTag(entry);
        }
        return list;
    }

    /** Runs a client handler the way {@code PacketQuesting} does, with the packet classified as host or remote. */
    private static void invokeAs(boolean host, Class<?> handler, NBTTagCompound payload) throws Exception {
        Method onClient = handler.getDeclaredMethod("onClient", NBTTagCompound.class);
        onClient.setAccessible(true);
        Exception[] failure = new Exception[1];
        IntegratedServerHost.runHandler(host, () -> {
            try {
                onClient.invoke(null, payload);
            } catch (InvocationTargetException e) {
                failure[0] = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
            } catch (IllegalAccessException e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) throw failure[0];
    }

    /** Dynamic stand-in that records which methods the handler called on it. */
    private static final class Recorder {

        final List<String> calls = new ArrayList<>();
        Object[] lastArgs;
        Object proxy;

        <T> T as(Class<T> type) {
            proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (self, method, args) -> {
                switch (method.getName()) {
                    case "equals":
                        return self == args[0];
                    case "hashCode":
                        return System.identityHashCode(self);
                    case "toString":
                        return type.getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(self));
                    default:
                        calls.add(method.getName());
                        lastArgs = args;
                        return defaultValue(method.getReturnType());
                }
            });
            return type.cast(proxy);
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
