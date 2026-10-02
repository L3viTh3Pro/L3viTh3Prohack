package com.example.chunkscout;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class ChunkScoutClient implements ClientModInitializer {

    public static final String MOD_ID = "chunkscout";

    public static boolean freecam = false;

    public static boolean storageFinder = true;
    public static boolean suspiciousChunks = true;
    public static boolean chests = true;
    public static boolean shulkers = true;
    public static boolean spawners = true;
    public static boolean anchors = true;

    /**
     * 0-1000 blocks.
     * 0 means only the player's current block.
     */
    public static int anchorRadius = 256;

    private static KeyBinding menuKey;
    private static KeyBinding freecamKey;

    private static final Set<Long> suspicious = new HashSet<>();
    private static final Set<BlockPos> anchorPositions = new HashSet<>();
    private static final Queue<Long> anchorScanQueue = new ArrayDeque<>();

    private static int scanTimer = 0;
    private static int anchorRefreshTimer = 0;

    public static double camX;
    public static double camY;
    public static double camZ;

    public static float camYaw;
    public static float camPitch;

    public static double freecamSpeed = 0.35;

    @Override
    public void onInitializeClient() {

        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.chunkscout.menu",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_RIGHT_CONTROL,
                        KeyBinding.Category.MISC
                )
        );

        freecamKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.chunkscout.freecam",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_F6,
                        KeyBinding.Category.MISC
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (menuKey.wasPressed()) {
                client.setScreen(new ConfigScreen());
            }

            if (freecamKey.wasPressed()) {
                toggleFreecam(client);
            }

            if (freecam) {
                tickFreecam(client);
            }

            if (++scanTimer >= 10) {
                scanTimer = 0;
                scanLoadedChunks(client);
            }

            if (++anchorRefreshTimer >= 100) {
                anchorRefreshTimer = 0;
                rebuildAnchorQueue(client);
            }

            scanOneAnchorChunk(client);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {

            if (ctx.matrices() == null
                    || ctx.consumers() == null
                    || ctx.world() == null
                    || ctx.camera() == null) {
                return;
            }

            MatrixStack matrices = ctx.matrices();

            Vec3d cam = ctx.camera().getCameraPos();

            VertexConsumer boxes =
                    ctx.consumers().getBuffer(RenderLayers.linesTranslucent());

            matrices.push();

            matrices.translate(
                    -cam.x,
                    -cam.y,
                    -cam.z
            );

            /*
             * Gyanús chunkok
             */
            if (suspiciousChunks) {

                for (long packed : suspicious) {

                    int cx = ChunkPos.getPackedX(packed);
                    int cz = ChunkPos.getPackedZ(packed);

                    Box box = new Box(
                            cx * 16.0,
                            ctx.world().getBottomY(),
                            cz * 16.0,

                            cx * 16.0 + 16.0,
                            ctx.world().getTopYInclusive() + 1.0,
                            cz * 16.0 + 16.0
                    );

                    drawBox(
                            matrices,
                            boxes,
                            box,
                            0x33FF0DCC
                    );
                }
            }

            /*
             * Storage Finder
             */
            if (storageFinder) {

                int pcx = (int) Math.floor(cam.x / 16.0);
                int pcz = (int) Math.floor(cam.z / 16.0);

                for (int cx = pcx - 12; cx <= pcx + 12; cx++) {

                    for (int cz = pcz - 12; cz <= pcz + 12; cz++) {

                        WorldChunk chunk =
                                ctx.world()
                                        .getChunkManager()
                                        .getWorldChunk(cx, cz, false);

                        if (chunk == null) {
                            continue;
                        }

                        for (Map.Entry<BlockPos, BlockEntity> entry
                                : chunk.getBlockEntities().entrySet()) {

                            BlockEntity be = entry.getValue();

                            int color;

                            if (chests && be instanceof ChestBlockEntity) {

                                color = 0xBFFFD900;

                            } else if (shulkers
                                    && be instanceof ShulkerBoxBlockEntity) {

                                color = 0xBFFF0DCC;

                            } else if (spawners
                                    && be instanceof MobSpawnerBlockEntity) {

                                color = 0xFF000000;

                            } else {

                                continue;
                            }

                            BlockPos pos = entry.getKey();

                            drawBox(
                                    matrices,
                                    boxes,
                                    new Box(pos),
                                    color
                            );
                        }
                    }
                }
            }

            /*
             * Respawn Anchor kereső
             */
            if (anchors) {

                for (BlockPos pos : anchorPositions) {

                    double dx =
                            (pos.getX() + 0.5) - cam.x;

                    double dy =
                            (pos.getY() + 0.5) - cam.y;

                    double dz =
                            (pos.getZ() + 0.5) - cam.z;

                    if (dx * dx + dy * dy + dz * dz
                            <= (double) anchorRadius * anchorRadius) {

                        drawBox(
                                matrices,
                                boxes,
                                new Box(pos),
                                0xD9FF2626
                        );
                    }
                }
            }

            matrices.pop();
        });
    }

    /**
     * Minecraft 1.21.11-ben a VertexRendering.drawBox helyett
     * drawOutline használható.
     */
    private static void drawBox(
            MatrixStack matrices,
            VertexConsumer vertices,
            Box box,
            int color
    ) {

        VertexRendering.drawOutline(
                matrices,
                vertices,
                VoxelShapes.cuboid(box),
                0.0,
                0.0,
                0.0,
                color,
                2.0f
        );
    }

    public static boolean isFreecam() {
        return freecam;
    }

    public static Vec3d getFreecamPos() {
        return new Vec3d(
                camX,
                camY,
                camZ
        );
    }

    public static float getFreecamYaw() {
        return camYaw;
    }

    public static float getFreecamPitch() {
        return camPitch;
    }

    public static void toggleFreecam(MinecraftClient client) {

        if (client.player == null) {
            return;
        }

        freecam = !freecam;

        if (freecam) {

            Vec3d pos = client.player.getEyePos();

            camX = pos.x;
            camY = pos.y;
            camZ = pos.z;

            camYaw = client.player.getYaw();
            camPitch = client.player.getPitch();
        }
    }

    private static void tickFreecam(MinecraftClient client) {

        if (client.player == null) {

            freecam = false;
            return;
        }

        long window =
                client.getWindow().getHandle();

        double yaw =
                Math.toRadians(camYaw);

        double pitch =
                Math.toRadians(camPitch);

        Vec3d forward = new Vec3d(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
        ).normalize();

        Vec3d right = new Vec3d(
                Math.cos(yaw),
                0,
                Math.sin(yaw)
        ).normalize();

        Vec3d move = Vec3d.ZERO;

        if (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_W
        ) == GLFW.GLFW_PRESS) {

            move = move.add(forward);
        }

        if (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_S
        ) == GLFW.GLFW_PRESS) {

            move = move.subtract(forward);
        }

        if (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_D
        ) == GLFW.GLFW_PRESS) {

            move = move.add(right);
        }

        if (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_A
        ) == GLFW.GLFW_PRESS) {

            move = move.subtract(right);
        }

        if (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_SPACE
        ) == GLFW.GLFW_PRESS) {

            move = move.add(0, 1, 0);
        }

        if (GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_LEFT_SHIFT
        ) == GLFW.GLFW_PRESS) {

            move = move.subtract(0, 1, 0);
        }

        if (move.lengthSquared() > 0) {

            double speed =
                    GLFW.glfwGetKey(
                            window,
                            GLFW.GLFW_KEY_LEFT_CONTROL
                    ) == GLFW.GLFW_PRESS
                            ? 1.2
                            : freecamSpeed;

            move = move.normalize().multiply(speed);

            camX += move.x;
            camY += move.y;
            camZ += move.z;
        }
    }

    private static void scanLoadedChunks(
            MinecraftClient client
    ) {

        if (client.world == null
                || client.player == null) {

            return;
        }

        suspicious.clear();

        int pcx =
                client.player.getChunkPos().x;

        int pcz =
                client.player.getChunkPos().z;

        for (int cx = pcx - 16;
             cx <= pcx + 16;
             cx++) {

            for (int cz = pcz - 16;
                 cz <= pcz + 16;
                 cz++) {

                WorldChunk chunk =
                        client.world
                                .getChunkManager()
                                .getWorldChunk(
                                        cx,
                                        cz,
                                        false
                                );

                if (chunk == null) {
                    continue;
                }

                int count = 0;

                for (BlockEntity be
                        : chunk.getBlockEntities().values()) {

                    if (be instanceof ChestBlockEntity
                            || be instanceof HopperBlockEntity
                            || be instanceof ShulkerBoxBlockEntity) {

                        count++;
                    }
                }

                if (count >= 5) {

                    suspicious.add(
                            ChunkPos.toLong(cx, cz)
                    );
                }
            }
        }
    }

    private static void rebuildAnchorQueue(
            MinecraftClient client
    ) {

        anchorScanQueue.clear();
        anchorPositions.clear();

        if (client.world == null
                || client.player == null) {

            return;
        }

        int chunkRadius =
                Math.max(
                        0,
                        (anchorRadius + 15) / 16
                );

        int pcx =
                client.player.getChunkPos().x;

        int pcz =
                client.player.getChunkPos().z;

        for (int cx = pcx - chunkRadius;
             cx <= pcx + chunkRadius;
             cx++) {

            for (int cz = pcz - chunkRadius;
                 cz <= pcz + chunkRadius;
                 cz++) {

                WorldChunk chunk =
                        client.world
                                .getChunkManager()
                                .getWorldChunk(
                                        cx,
                                        cz,
                                        false
                                );

                if (chunk != null) {

                    anchorScanQueue.add(
                            ChunkPos.toLong(cx, cz)
                    );
                }
            }
        }
    }

    private static void scanOneAnchorChunk(
            MinecraftClient client
    ) {

        if (!anchors
                || client.world == null
                || client.player == null) {

            return;
        }

        if (anchorScanQueue.isEmpty()) {
            return;
        }

        long packed =
                anchorScanQueue.poll();

        int cx =
                ChunkPos.getPackedX(packed);

        int cz =
                ChunkPos.getPackedZ(packed);

        WorldChunk chunk =
                client.world
                        .getChunkManager()
                        .getWorldChunk(
                                cx,
                                cz,
                                false
                        );

        if (chunk == null) {
            return;
        }

        int minY =
                client.world.getBottomY();

        int maxY =
                client.world.getTopYInclusive();

        BlockPos.Mutable pos =
                new BlockPos.Mutable();

        for (int x = 0; x < 16; x++) {

            for (int z = 0; z < 16; z++) {

                for (int y = minY; y <= maxY; y++) {

                    pos.set(
                            cx * 16 + x,
                            y,
                            cz * 16 + z
                    );

                    if (client.world
                            .getBlockState(pos)
                            .isOf(Blocks.RESPAWN_ANCHOR)) {

                        double dx =
                                pos.getX()
                                        + 0.5
                                        - client.player.getX();

                        double dy =
                                pos.getY()
                                        + 0.5
                                        - client.player.getY();

                        double dz =
                                pos.getZ()
                                        + 0.5
                                        - client.player.getZ();

                        if (dx * dx
                                + dy * dy
                                + dz * dz
                                <= (double) anchorRadius
                                * anchorRadius) {

                            anchorPositions.add(
                                    pos.toImmutable()
                            );
                        }
                    }
                }
            }
        }
    }

    private static Text label(
            String name,
            boolean on
    ) {

        return Text.literal(
                name + ": " + (on ? "BE" : "KI")
        );
    }

    public static class ConfigScreen
            extends Screen {

        protected ConfigScreen() {

            super(
                    Text.literal(
                            "ChunkScout beállítások"
                    )
            );
        }

        @Override
        protected void init() {

            int x =
                    this.width / 2 - 210;

            int y =
                    this.height / 2 - 100;

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Freecam",
                                    freecam
                            ),
                            button -> {

                                toggleFreecam(
                                        MinecraftClient.getInstance()
                                );

                                button.setMessage(
                                        label(
                                                "Freecam",
                                                freecam
                                        )
                                );
                            }
                    ).dimensions(
                            x,
                            y,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Storage Finder",
                                    storageFinder
                            ),
                            button -> {

                                storageFinder =
                                        !storageFinder;

                                button.setMessage(
                                        label(
                                                "Storage Finder",
                                                storageFinder
                                        )
                                );
                            }
                    ).dimensions(
                            x,
                            y + 25,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Gyanús chunkok",
                                    suspiciousChunks
                            ),
                            button -> {

                                suspiciousChunks =
                                        !suspiciousChunks;

                                button.setMessage(
                                        label(
                                                "Gyanús chunkok",
                                                suspiciousChunks
                                        )
                                );
                            }
                    ).dimensions(
                            x,
                            y + 50,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Ládák",
                                    chests
                            ),
                            button -> {

                                chests =
                                        !chests;

                                button.setMessage(
                                        label(
                                                "Ládák",
                                                chests
                                        )
                                );
                            }
                    ).dimensions(
                            x,
                            y + 75,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Shulkerek",
                                    shulkers
                            ),
                            button -> {

                                shulkers =
                                        !shulkers;

                                button.setMessage(
                                        label(
                                                "Shulkerek",
                                                shulkers
                                        )
                                );
                            }
                    ).dimensions(
                            x + 210,
                            y,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Spawner",
                                    spawners
                            ),
                            button -> {

                                spawners =
                                        !spawners;

                                button.setMessage(
                                        label(
                                                "Spawner",
                                                spawners
                                        )
                                );
                            }
                    ).dimensions(
                            x + 210,
                            y + 25,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            label(
                                    "Anchor",
                                    anchors
                            ),
                            button -> {

                                anchors =
                                        !anchors;

                                button.setMessage(
                                        label(
                                                "Anchor",
                                                anchors
                                        )
                                );
                            }
                    ).dimensions(
                            x + 210,
                            y + 50,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            Text.literal(
                                    "Anchor radius: "
                                            + anchorRadius
                            ),
                            button -> {

                                anchorRadius += 64;

                                if (anchorRadius > 1000) {
                                    anchorRadius = 0;
                                }

                                rebuildAnchorQueue(
                                        MinecraftClient.getInstance()
                                );

                                button.setMessage(
                                        Text.literal(
                                                "Anchor radius: "
                                                        + anchorRadius
                                        )
                                );
                            }
                    ).dimensions(
                            x + 210,
                            y + 75,
                            200,
                            20
                    ).build()
            );

            addDrawableChild(
                    ButtonWidget.builder(
                            Text.literal("Bezárás"),
                            button -> close()
                    ).dimensions(
                            this.width / 2 - 100,
                            y + 115,
                            200,
                            20
                    ).build()
            );
        }

        @Override
        public void render(
                net.minecraft.client.gui.DrawContext context,
                int mouseX,
                int mouseY,
                float delta
        ) {

            renderBackground(
                    context,
                    mouseX,
                    mouseY,
                    delta
            );

            context.drawCenteredTextWithShadow(
                    textRenderer,
                    title,
                    width / 2,
                    height / 2 - 135,
                    0xFFFFFF
            );

            super.render(
                    context,
                    mouseX,
                    mouseY,
                    delta
            );
        }
    }
}
