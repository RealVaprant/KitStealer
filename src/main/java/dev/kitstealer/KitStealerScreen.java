package dev.kitstealer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

public final class KitStealerScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 360;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int SCREEN_MARGIN = 8;
    private static final int KIT_ROW_HEIGHT = 20;
    private static final int PANEL_BACKGROUND = 0xF0101010;
    private static final int PANEL_BORDER = 0xFF777777;
    private static final int KIT_ROW_BACKGROUND = 0xAA101010;
    private static final int KIT_ROW_HOVER_BACKGROUND = 0xAA2A2A2A;
    private static final int KIT_ROW_SELECTED_BACKGROUND = 0xAA3A3A3A;
    private static final int KIT_ROW_MULTI_SELECTED_BACKGROUND = 0xAA665500;
    private static final int KIT_ROW_BORDER = 0xFF555555;
    private static final int KIT_ROW_SELECTED_BORDER = 0xFFFFFFFF;
    private static final int KIT_ROW_MULTI_SELECTED_BORDER = 0xFFFFFF55;
    private static final int SECONDARY_TEXT = 0xFFAAAAAA;
    private static final int CONFIRMATION_TEXT = 0xFFFF5555;
    private static final int MULTI_SELECTED_TEXT = 0xFFFFFF55;
    private static final Component LOAD_SELECTED_LABEL = Component.literal("Load Selected");
    private static final Component DELETE_LABEL = Component.literal("Delete");
    private static final Component CONFIRM_LABEL = Component.literal("Confirm");
    private static final Component CANCEL_LABEL = Component.literal("Cancel");
    private final Screen parentScreen;
    private final KitStore kitStore;
    private final List<UUID> selectedKitIds = new ArrayList<>();
    private UUID rangeAnchorKitId;
    private InventoryKit selectedKit;
    private InventoryKit draggedKit;
    private EditBox kitNameField;
    private Button loadButton;
    private Button deleteButton;
    private KitSelectionList kitList;
    private Component statusMessage;
    private boolean deleteConfirmationActive;
    private boolean rightDragActive;
    private int panelWidth;
    private int panelHeight;
    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listBottom;
    private int footerTop;

    private KitStealerScreen(Screen parentScreen, KitStore kitStore) {
        super(Component.literal("Kit Stealer"));
        this.parentScreen = parentScreen;
        this.kitStore = kitStore;
    }

    public static void open(Minecraft minecraft, KitStore kitStore) {
        if (minecraft.player == null) {
            return;
        }

        Screen parentScreen = minecraft.screen;
        minecraft.setScreen(new KitStealerScreen(parentScreen, kitStore));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(0, width - SCREEN_MARGIN * 2));
        panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(0, height - SCREEN_MARGIN * 2));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        listTop = panelTop + 72;
        footerTop = panelTop + panelHeight - 28;
        listBottom = footerTop - 18;

        int contentWidth = Math.max(0, panelWidth - 32);
        int maximumStealButtonWidth = Math.max(0, contentWidth - 8);
        int stealButtonWidth = Math.min(maximumStealButtonWidth, Math.min(130, Math.max(80, (contentWidth - 8) / 3)));
        int kitNameFieldWidth = Math.max(0, contentWidth - stealButtonWidth - 8);

        kitNameField = addRenderableWidget(new EditBox(
                font,
                panelLeft + 16,
                panelTop + 38,
                kitNameFieldWidth,
                20,
                Component.literal("Kit name")
        ));
        kitNameField.setMaxLength(32);
        kitNameField.setHint(Component.literal("Name this inventory"));

        addRenderableWidget(Button.builder(
                Component.literal("Steal Inventory"),
                button -> stealInventory()
        ).bounds(panelLeft + 16 + kitNameFieldWidth + 8, panelTop + 38, stealButtonWidth, 20).build());

        int footerWidth = Math.max(0, panelWidth - 32);
        int footerButtonSpace = Math.max(0, footerWidth - 16);
        int actionButtonWidth = footerButtonSpace / 3;
        int loadButtonWidth = actionButtonWidth;
        int deleteButtonWidth = actionButtonWidth;
        int closeButtonWidth = Math.max(0, footerButtonSpace - loadButtonWidth - deleteButtonWidth);
        int loadButtonLeft = panelLeft + 16;
        int deleteButtonLeft = loadButtonLeft + loadButtonWidth + 8;
        int closeButtonLeft = deleteButtonLeft + deleteButtonWidth + 8;

        loadButton = addRenderableWidget(Button.builder(
                LOAD_SELECTED_LABEL,
                button -> handleLoadButtonPress()
        ).bounds(loadButtonLeft, footerTop, loadButtonWidth, 20).build());

        deleteButton = addRenderableWidget(Button.builder(
                DELETE_LABEL,
                button -> handleDeleteButtonPress()
        ).bounds(deleteButtonLeft, footerTop, deleteButtonWidth, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Close"),
                button -> onClose()
        ).bounds(closeButtonLeft, footerTop, closeButtonWidth, 20).build());

        int listLeft = panelLeft + 16;
        int listWidth = Math.max(0, panelWidth - 32);
        int listHeight = Math.max(0, listBottom - listTop);
        kitList = addRenderableWidget(new KitSelectionList(minecraft, listWidth, listHeight, listTop, KIT_ROW_HEIGHT));
        kitList.updateSizeAndPosition(listWidth, listHeight, listLeft, listTop);
        rebuildKitList();
        updateActionButtons();
    }

    @Override
    public void tick() {
        updateActionButtons();
        super.tick();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL_BACKGROUND);
        guiGraphics.renderOutline(panelLeft, panelTop, panelWidth, panelHeight, PANEL_BORDER);
        guiGraphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFFFFF);
        guiGraphics.drawString(font, Component.literal("Stolen Inventories:"), panelLeft + 16, panelTop + 62, SECONDARY_TEXT, false);
        guiGraphics.renderOutline(kitList.getX(), kitList.getY(), kitList.getWidth(), kitList.getHeight(), PANEL_BORDER);

        if (minecraft.player != null && !minecraft.player.isCreative() && statusMessage == null) {
            guiGraphics.drawString(font, Component.literal("Creative mode is required to load"), panelLeft + 16, footerTop - 16, 0xFFFFAA00, false);
        }

        if (statusMessage != null) {
            int statusColor = deleteConfirmationActive ? CONFIRMATION_TEXT : 0xFF8FD18F;
            guiGraphics.drawString(font, statusMessage, panelLeft + 16, footerTop - 16, statusColor, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean clickedKitNameField = kitNameField != null && kitNameField.isMouseOver(event.x(), event.y());
        boolean clickedListContent = kitList != null && kitList.isOverListContent(event.x(), event.y());
        boolean clickedKitEntry = kitList != null && kitList.isOverKitEntry(event.x(), event.y());
        if (!clickedKitNameField) {
            clearFocus();
        }
        if (clickedKitNameField || (clickedListContent && !clickedKitEntry)) {
            clearSelectedKit();
        }

        if (event.button() == 1 && kitList.isMouseOver(event.x(), event.y())) {
            if (kitList.beginRightDrag(event)) {
                rightDragActive = true;
                setFocused(kitList);
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 1 && rightDragActive) {
            return kitList.dragRight(event);
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 1 && rightDragActive) {
            rightDragActive = false;
            draggedKit = null;
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    private void stealInventory() {
        if (minecraft.player == null || minecraft.level == null) {
            statusMessage = Component.literal("No player inventory is available");
            return;
        }

        String kitName = createUniqueKitName(kitNameField.getValue().trim());
        HolderLookup.Provider registryAccess = minecraft.level.registryAccess();
        InventoryKit stolenKit = InventoryKit.capturePlayerInventory(
                UUID.randomUUID(),
                kitName,
                kitStore.nextPriority(),
                minecraft.player.inventoryMenu,
                registryAccess
        );
        kitStore.save(stolenKit);
        statusMessage = Component.literal("Saved " + stolenKit.getName());
        rebuildKitList();
        updateActionButtons();
    }

    private String createUniqueKitName(String requestedName) {
        String baseName = requestedName.isEmpty()
                ? "Inventory " + (kitStore.getKits().size() + 1)
                : requestedName;
        if (hasKitName(baseName)) {
            int duplicateNumber = 2;
            String duplicateName = baseName + " (" + duplicateNumber + ")";
            while (hasKitName(duplicateName)) {
                duplicateNumber++;
                duplicateName = baseName + " (" + duplicateNumber + ")";
            }
            return duplicateName;
        }

        return baseName;
    }

    private boolean hasKitName(String kitName) {
        return kitStore.getKits().stream().anyMatch(kit -> kit.getName().equals(kitName));
    }

    private void handleLoadButtonPress() {
        if (deleteConfirmationActive) {
            cancelDeleteConfirmation();
            return;
        }

        loadSelectedKit();
    }

    private void handleDeleteButtonPress() {
        if (deleteConfirmationActive) {
            confirmDeleteSelectedKit();
            return;
        }

        requestDeleteConfirmation();
    }

    private void loadSelectedKit() {
        if (!hasSingleSelectedKit()) {
            statusMessage = selectedStatusMessage();
            return;
        }

        if (minecraft.player == null || !minecraft.player.isCreative()) {
            statusMessage = Component.literal("Creative mode is required");
            updateActionButtons();
            return;
        }

        if (!selectedKit.loadIntoPlayer(minecraft)) {
            statusMessage = Component.literal("Could not load this inventory");
            return;
        }

        statusMessage = Component.literal("Loaded " + selectedKit.getName());
    }

    private void requestDeleteConfirmation() {
        if (!hasSelectedKits()) {
            return;
        }

        deleteConfirmationActive = true;
        statusMessage = selectedKitIds.size() == 1
                ? Component.literal("Are you sure you want to delete " + selectedKit.getName() + "?")
                : Component.literal("Are you sure you want to delete " + selectedKitIds.size() + " Inventories?");
        updateActionButtons();
    }

    private void cancelDeleteConfirmation() {
        deleteConfirmationActive = false;
        statusMessage = selectedStatusMessage();
        updateActionButtons();
    }

    private void confirmDeleteSelectedKit() {
        List<InventoryKit> kitsToDelete = getSelectedKits();
        if (kitsToDelete.isEmpty()) {
            return;
        }

        int deletedKitCount = kitsToDelete.size();
        String deletedKitName = selectedKit.getName();
        kitsToDelete.forEach(kitStore::delete);
        selectedKitIds.clear();
        selectedKit = null;
        rangeAnchorKitId = null;
        deleteConfirmationActive = false;
        kitNameField.setValue("");
        statusMessage = deletedKitCount == 1
                ? Component.literal("Deleted " + deletedKitName)
                : Component.literal("Deleted " + deletedKitCount + " Inventories");
        rebuildKitList();
        updateActionButtons();
    }

    private void rebuildKitList() {
        kitList.replaceKits();
    }

    private void selectKit(InventoryKit kit) {
        selectedKitIds.clear();
        selectedKitIds.add(kit.getId());
        selectedKit = kit;
        rangeAnchorKitId = kit.getId();
        deleteConfirmationActive = false;
        statusMessage = selectedStatusMessage();
        kitList.selectKit(kit);
        updateActionButtons();
    }

    private void addKitToSelection(InventoryKit kit) {
        if (!isKitSelected(kit)) {
            selectedKitIds.add(kit.getId());
        }

        selectedKit = kit;
        refreshSelectionState();
    }

    private void toggleKitSelection(InventoryKit kit) {
        if (isKitSelected(kit)) {
            boolean wasRangeAnchor = kit.getId().equals(rangeAnchorKitId);
            selectedKitIds.remove(kit.getId());
            selectedKit = selectedKitIds.isEmpty()
                    ? null
                    : findKitById(selectedKitIds.get(selectedKitIds.size() - 1));
            if (selectedKit == null) {
                rangeAnchorKitId = null;
            } else if (wasRangeAnchor) {
                rangeAnchorKitId = selectedKit.getId();
            }
        } else {
            selectedKitIds.add(kit.getId());
            selectedKit = kit;
            rangeAnchorKitId = kit.getId();
        }

        refreshSelectionState();
    }

    private void selectKitRange(InventoryKit kit, boolean preserveExistingSelection) {
        if (selectedKit == null) {
            selectKit(kit);
            return;
        }

        UUID anchorKitId = rangeAnchorKitId == null ? selectedKit.getId() : rangeAnchorKitId;
        List<InventoryKit> kits = kitStore.getKits();
        int anchorIndex = findKitIndex(kits, anchorKitId);
        int clickedIndex = findKitIndex(kits, kit.getId());
        if (anchorIndex < 0 || clickedIndex < 0) {
            selectKit(kit);
            return;
        }

        if (!preserveExistingSelection) {
            selectedKitIds.clear();
        }

        int firstIndex = Math.min(anchorIndex, clickedIndex);
        int lastIndex = Math.max(anchorIndex, clickedIndex);
        for (int index = firstIndex; index <= lastIndex; index++) {
            UUID kitId = kits.get(index).getId();
            if (!selectedKitIds.contains(kitId)) {
                selectedKitIds.add(kitId);
            }
        }

        selectedKit = kit;
        refreshSelectionState();
    }

    private int findKitIndex(List<InventoryKit> kits, UUID kitId) {
        for (int index = 0; index < kits.size(); index++) {
            if (kits.get(index).getId().equals(kitId)) {
                return index;
            }
        }

        return -1;
    }

    private InventoryKit findKitById(UUID kitId) {
        return kitStore.getKits().stream()
                .filter(kit -> kit.getId().equals(kitId))
                .findFirst()
                .orElse(null);
    }

    private void refreshSelectionState() {
        deleteConfirmationActive = false;
        statusMessage = selectedStatusMessage();
        kitList.selectKit(selectedKit);
        updateActionButtons();
    }

    private void clearSelectedKit() {
        if (!hasSelectedKits() && !deleteConfirmationActive) {
            return;
        }

        selectedKitIds.clear();
        selectedKit = null;
        rangeAnchorKitId = null;
        deleteConfirmationActive = false;
        statusMessage = null;
        kitList.selectKit(null);
        updateActionButtons();
    }

    private boolean selectAdjacentKit(boolean moveUp) {
        if (selectedKit == null) {
            return false;
        }

        List<InventoryKit> kits = kitStore.getKits();
        int selectedIndex = -1;
        for (int index = 0; index < kits.size(); index++) {
            if (kits.get(index).getId().equals(selectedKit.getId())) {
                selectedIndex = index;
                break;
            }
        }

        int adjacentIndex = selectedIndex + (moveUp ? -1 : 1);
        if (selectedIndex < 0 || adjacentIndex < 0 || adjacentIndex >= kits.size()) {
            return false;
        }

        selectKit(kits.get(adjacentIndex));
        return true;
    }

    private boolean moveSelectedKit(boolean moveUp) {
        if (selectedKit == null) {
            return false;
        }

        InventoryKit movedKit = moveUp ? kitStore.moveUp(selectedKit) : kitStore.moveDown(selectedKit);
        if (movedKit == null) {
            return false;
        }

        selectedKit = movedKit;
        if (rightDragActive) {
            draggedKit = movedKit;
        }
        rebuildKitList();
        kitList.selectKit(selectedKit);
        updateActionButtons();
        return true;
    }

    private boolean hasSelectedKits() {
        return !selectedKitIds.isEmpty();
    }

    private boolean hasSingleSelectedKit() {
        return selectedKitIds.size() == 1 && selectedKit != null;
    }

    private boolean isKitSelected(InventoryKit kit) {
        return selectedKitIds.contains(kit.getId());
    }

    private boolean isActiveKit(InventoryKit kit) {
        return selectedKit != null && kit.getId().equals(selectedKit.getId());
    }

    private List<InventoryKit> getSelectedKits() {
        return kitStore.getKits().stream()
                .filter(this::isKitSelected)
                .toList();
    }

    private Component selectedStatusMessage() {
        if (selectedKitIds.size() > 1) {
            return Component.literal("Selected " + selectedKitIds.size() + " Inventories");
        }

        if (selectedKit == null) {
            return null;
        }

        return Component.literal("Selected " + selectedKit.getName());
    }

    private void updateActionButtons() {
        if (loadButton == null || deleteButton == null) {
            return;
        }

        if (deleteConfirmationActive) {
            loadButton.setMessage(CANCEL_LABEL);
            deleteButton.setMessage(CONFIRM_LABEL);
            loadButton.active = hasSelectedKits();
            deleteButton.active = hasSelectedKits();
            return;
        }

        loadButton.setMessage(LOAD_SELECTED_LABEL);
        deleteButton.setMessage(DELETE_LABEL);
        loadButton.active = hasSingleSelectedKit() && minecraft.player != null && minecraft.player.isCreative();
        deleteButton.active = hasSelectedKits();
    }

    private final class KitSelectionList extends ObjectSelectionList<KitEntry> {
        private KitSelectionList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        private void replaceKits() {
            replaceEntries(kitStore.getKits().stream().map(KitEntry::new).toList());
            selectKit(selectedKit);
        }

        private void selectKit(InventoryKit kit) {
            KitEntry selectedEntry = kit == null
                    ? null
                    : children().stream()
                            .filter(entry -> entry.kit.getId().equals(kit.getId()))
                            .findFirst()
                            .orElse(null);
            setSelected(selectedEntry);
        }

        private boolean isOverListContent(double mouseX, double mouseY) {
            return isMouseOver(mouseX, mouseY) && mouseX < scrollBarX();
        }

        private boolean isOverKitEntry(double mouseX, double mouseY) {
            return getEntryAtPosition(mouseX, mouseY) != null;
        }

        private boolean beginRightDrag(MouseButtonEvent event) {
            KitEntry entry = getEntryAtPosition(event.x(), event.y());
            if (entry == null) {
                return false;
            }

            selectKit(entry.kit);
            draggedKit = entry.kit;
            return true;
        }

        private boolean dragRight(MouseButtonEvent event) {
            if (draggedKit == null) {
                return false;
            }

            KitEntry targetEntry = getEntryAtPosition(event.x(), event.y());
            if (targetEntry == null || targetEntry.kit == draggedKit) {
                return true;
            }

            KitEntry draggedEntry = children().stream()
                    .filter(entry -> entry.kit.getId().equals(draggedKit.getId()))
                    .findFirst()
                    .orElse(null);
            if (draggedEntry == null) {
                return true;
            }

            int draggedIndex = children().indexOf(draggedEntry);
            int targetIndex = children().indexOf(targetEntry);
            if (targetIndex < draggedIndex) {
                moveSelectedKit(true);
            } else if (targetIndex > draggedIndex) {
                moveSelectedKit(false);
            }
            return true;
        }

        @Override
        public int getRowWidth() {
            return Math.max(0, getWidth() - 20);
        }

        @Override
        protected void renderListSeparators(GuiGraphics guiGraphics) {
        }

        @Override
        protected void renderSelection(GuiGraphics guiGraphics, KitEntry entry, int color) {
        }
    }

    private final class KitEntry extends ObjectSelectionList.Entry<KitEntry> {
        private final InventoryKit kit;

        private KitEntry(InventoryKit kit) {
            this.kit = kit;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean isSelected = isKitSelected(kit);
            boolean isActiveSelection = isActiveKit(kit);
            boolean isMultiSelected = isSelected && selectedKitIds.size() > 1;
            int backgroundColor = isMultiSelected
                    ? KIT_ROW_MULTI_SELECTED_BACKGROUND
                    : isActiveSelection ? KIT_ROW_SELECTED_BACKGROUND : hovered ? KIT_ROW_HOVER_BACKGROUND : KIT_ROW_BACKGROUND;
            int borderColor = isActiveSelection
                    ? KIT_ROW_SELECTED_BORDER
                    : isMultiSelected ? KIT_ROW_MULTI_SELECTED_BORDER : KIT_ROW_BORDER;
            guiGraphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, backgroundColor);
            guiGraphics.renderOutline(getX(), getY(), getWidth(), getHeight(), borderColor);

            int textColor = hovered || isSelected ? 0xFFFFFFFF : 0xFFE0E0E0;
            int nameTextTop = getContentY() + (getHeight() - font.lineHeight) / 2;
            guiGraphics.drawString(font, Component.literal(kit.getName()), getContentX() + 8, nameTextTop, textColor, false);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            boolean isVerticalMovement = event.isUp() || event.isDown();
            if (!isVerticalMovement) {
                return false;
            }

            if (event.hasControlDown() && event.hasShiftDown()) {
                return moveSelectedKit(event.isUp());
            }

            return selectAdjacentKit(event.isUp());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return false;
            }

            if (event.hasControlDown() && event.hasShiftDown()) {
                selectKitRange(kit, true);
            } else if (event.hasControlDown()) {
                toggleKitSelection(kit);

            } else if (event.hasShiftDown()) {
                selectKitRange(kit, false);
            } else {
                selectKit(kit);
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal("Inventory kit " + kit.getName());
        }
    }
}
