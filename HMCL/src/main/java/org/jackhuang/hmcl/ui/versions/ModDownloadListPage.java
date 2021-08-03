/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadListPage extends Control implements DecoratorPage {
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();
    private final ListProperty<CurseAddon> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final ModDownloadPage.DownloadCallback callback;

    /**
     * @see org.jackhuang.hmcl.mod.curse.CurseModManager#SECTION_MODPACK
     * @see org.jackhuang.hmcl.mod.curse.CurseModManager#SECTION_MOD
     */
    private final int section;

    public ModDownloadListPage(int section, ModDownloadPage.DownloadCallback callback) {
        this.section = section;
        this.callback = callback;
    }

    public void loadVersion(Profile profile, String version) {
        this.version.set(new Profile.ProfileVersion(profile, version));

        setLoading(false);
        setFailed(false);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public void search(String userGameVersion, int category, int pageOffset, String searchFilter, int sort) {
        setLoading(true);
        File versionJar = StringUtils.isNotBlank(version.get().getVersion())
                ? version.get().getProfile().getRepository().getVersionJar(version.get().getVersion())
                : null;
        Task.supplyAsync(() -> {
            String gameVersion;
            if (StringUtils.isBlank(version.get().getVersion())) {
                gameVersion = userGameVersion;
            } else {
                gameVersion = GameVersion.minecraftVersion(versionJar).orElse("");
            }
            return gameVersion;
        }).thenApplyAsync(gameVersion -> {
            return CurseModManager.searchPaginated(gameVersion, category, section, pageOffset, searchFilter, sort);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            setLoading(false);
            if (exception == null) {
                items.setAll(result);
            } else {
                failed.set(true);
            }
        }).start();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadListPageSkin(this);
    }

    private static class ModDownloadListPageSkin extends SkinBase<ModDownloadListPage> {

        protected ModDownloadListPageSkin(ModDownloadListPage control) {
            super(control);

            VBox pane = new VBox();

            GridPane searchPane = new GridPane();
            searchPane.getStyleClass().addAll("card");
            VBox.setMargin(searchPane, new Insets(10, 10, 0, 10));

            ColumnConstraints column1 = new ColumnConstraints();
            column1.setPercentWidth(50);
            column1.setHgrow(Priority.ALWAYS);
            ColumnConstraints column2 = new ColumnConstraints();
            column2.setHgrow(Priority.ALWAYS);
            column2.setPercentWidth(50);
            searchPane.getColumnConstraints().setAll(column1, column2);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                JFXTextField nameField = new JFXTextField();
                nameField.setPromptText(i18n("mods.name"));
                searchPane.add(nameField, 0, 0);

                JFXTextField gameVersionField = new JFXTextField();
                gameVersionField.setPromptText(i18n("world.game_version"));
                searchPane.add(gameVersionField, 1, 0);

                FXUtils.onChangeAndOperate(getSkinnable().version, version -> {
                    searchPane.getChildren().remove(gameVersionField);
                    if (StringUtils.isNotBlank(version.getVersion())) {
                        GridPane.setColumnSpan(nameField, 2);
                    } else {
                        searchPane.add(gameVersionField, 1, 0);
                        GridPane.setColumnSpan(nameField, 1);
                    }
                });

                JFXTextField categoryField = new JFXTextField();
                categoryField.setPromptText(i18n("mods.category"));
                searchPane.add(categoryField, 0, 1);

                JFXTextField sortField = new JFXTextField();
                sortField.setPromptText(i18n("search.sort"));
                searchPane.add(sortField, 1, 1);

                VBox vbox = new VBox();
                vbox.setAlignment(Pos.CENTER_RIGHT);
                searchPane.add(vbox, 0, 2, 2, 1);

                JFXButton searchButton = new JFXButton();
                searchButton.setText(i18n("search"));
                searchButton.setOnAction(e -> {
                    getSkinnable().search(gameVersionField.getText(), 0, 0, nameField.getText(), 0);
                });
                searchPane.add(searchButton, 0, 2);
                vbox.getChildren().setAll(searchButton);
            }

            SpinnerPane spinnerPane = new SpinnerPane();
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(Bindings.createStringBinding(() -> {
                    if (getSkinnable().isFailed()) {
                        return i18n("download.failed.refresh");
                    } else {
                        return null;
                    }
                }, getSkinnable().failedProperty()));

                JFXListView<CurseAddon> listView = new JFXListView<>();
                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setOnMouseClicked(e -> {
                    if (listView.getSelectionModel().getSelectedIndex() < 0)
                        return;
                    CurseAddon selectedItem = listView.getSelectionModel().getSelectedItem();
                    Controllers.navigate(new ModDownloadPage(selectedItem, getSkinnable().version.get(), getSkinnable().callback));
                });
                listView.setCellFactory(x -> new FloatListCell<CurseAddon>() {
                    TwoLineListItem content = new TwoLineListItem();
                    ImageView imageView = new ImageView();

                    {
                        Region clippedContainer = (Region) listView.lookup(".clipped-container");
                        setPrefWidth(0);
                        HBox container = new HBox(8);
                        container.setAlignment(Pos.CENTER_LEFT);
                        pane.getChildren().add(container);
                        if (clippedContainer != null) {
                            maxWidthProperty().bind(clippedContainer.widthProperty());
                            prefWidthProperty().bind(clippedContainer.widthProperty());
                            minWidthProperty().bind(clippedContainer.widthProperty());
                        }

                        container.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);
                    }

                    @Override
                    protected void updateControl(CurseAddon dataItem, boolean empty) {
                        if (empty) return;
                        content.setTitle(dataItem.getName());
                        content.setSubtitle(dataItem.getSummary());
                        content.getTags().setAll(dataItem.getCategories().stream()
                                .map(category -> i18n("curse.category." + category.getCategoryId()))
                                .collect(Collectors.toList()));

                        for (CurseAddon.Attachment attachment : dataItem.getAttachments()) {
                            if (attachment.isDefault()) {
                                imageView.setImage(new Image(attachment.getThumbnailUrl(), 40, 40, true, true, true));
                            }
                        }
                    }
                });
            }

            pane.getChildren().setAll(searchPane, spinnerPane);

            getChildren().setAll(pane);
        }
    }
}
