package gui.ctrl.bar;

import gui.ctrl.FilterConfigView;
import gui.ctrl.IndexView;
import gui.ctrl.View;
import gui.model.FilterProperty;
import gui.model.SettingProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.FileHandle;
import util.ViewHandle;

import java.io.IOException;
import java.util.List;

public class FilterBar {
    View view;
    private int selectIndex;

    @FXML
    Button configButton;

    ContextMenu configMenu;
    MenuItem managerItem;

    @FXML
    Button clearButton;

    @FXML
    Button applyButton;

    @FXML
    ComboBox<String> filterBox;

    public FilterBar() {}

    public void initialize() {

        ViewHandle.InitializeFilterComboBox(SettingProperty.filterHistory, filterBox);
        Image clearImage = new Image(getClass().getResourceAsStream(SettingProperty.iconFolder3 + "/x-filter-clear.active.png"));
        clearButton.setGraphic(new ImageView(clearImage));
        Image applyImage = new Image(getClass().getResourceAsStream(SettingProperty.iconFolder2 + "/x-filter-apply.png"));
        applyButton.setGraphic(new ImageView(applyImage));
        Image configImage = new Image(getClass().getResourceAsStream(SettingProperty.iconFolder3 + "/x-display-filter-bookmark.png"));
        configButton.setGraphic(new ImageView(configImage));

        configMenu = new ContextMenu();
        managerItem = new MenuItem();
        this.InitialContextMenu();

        filterBox.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent keyEvent) {
                if (keyEvent.getCode()==KeyCode.ENTER)
                    ApplyButtonOnClicked();
            }
        });

    }

    private void InitialContextMenu() {
        managerItem.setText("Manager");
        managerItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    FXMLLoader loader = ViewHandle.GetLoader("gui/view/FilterConfigView.fxml");
                    AnchorPane managerPane = loader.load();
                    Stage stage = new Stage();
                    stage.initStyle(StageStyle.DECORATED);
                    stage.initModality(Modality.APPLICATION_MODAL);

                    Scene scene = new Scene(managerPane);
                    stage.setScene(scene);

                    FilterConfigView filterConfigView = loader.getController();
                    filterConfigView.setFilterBar(self());

                    stage.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        configMenu.getItems().addAll(managerItem, new SeparatorMenuItem());
        configButton.setContextMenu(configMenu);

        this.UpdateContextMenu();
    }

    private FilterBar self() {
        return this;
    }


    public void UpdateContextMenu() {
        configMenu.getItems().remove(2, configMenu.getItems().size());
        ToggleGroup group = new ToggleGroup();
        List<FilterProperty> list = FileHandle.ReadJson(SettingProperty.filterConfig, FilterProperty.class);
        assert list != null;
        list.forEach(p -> {
            RadioMenuItem item = new RadioMenuItem(p.getName()
                    + ":" + p.getExpression());
            item.setToggleGroup(group);
            configMenu.getItems().add(item);
            item.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    IndexView indexView = (IndexView) view;
                    if (item.isSelected()) {
                        for (FilterProperty property : list) {
                            if (item.getText().equals(property.getName())) {
                                filterBox.setValue(property.getExpression());
                                indexView.setFilterProperty(property);
                                break;
                            }
                        }
                    }
                }
            });
        });

        if (selectIndex>1)
            ((RadioMenuItem)configMenu.getItems().get(selectIndex)).setSelected(true);

    }

    public void setView(View view) {
        this.view = view;
        IndexView indexView = (IndexView) view;
        if (indexView.getFilterProperty()==null && configMenu.getItems().size()>2){
            selectIndex = 2;
            RadioMenuItem item = (RadioMenuItem) configMenu.getItems().get(selectIndex);
            item.setSelected(true);
            List<FilterProperty> list = FileHandle.ReadJson(SettingProperty.filterConfig, FilterProperty.class);
            assert list != null;
            for (FilterProperty property : list) {
                if (item.getText().contains(property.getName())) {
                    indexView.setFilterProperty(property);
                    break;
                }
            }
        }
    }

    @FXML
    private void ConfigButtonOnClicked() {
        configMenu.show(configButton, Side.BOTTOM, 0, 0);
    }

    @FXML
    private void ClearButtonOnClicked() {
        if (filterBox.getValue()==null)
            filterBox.setValue("");
        if (filterBox.getValue().equals(""))
            return;
        String type = view.getType();
        IndexView indexView = (IndexView) view;
        if (filterBox.getValue().equals(indexView.getFilterProperty().getExpression()))
            indexView.getFilterProperty().setExpression("");
        filterBox.setValue("");
        if (type.equals("capture")) {
            indexView.getPacketListCtrl().getPacketTable().getItems().clear();
            indexView.getFilterProperty().setExpression("");
            indexView.StartCapture("apply");
        }
    }

    @FXML
    private void ApplyButtonOnClicked() {
        String type = view.getType();
        IndexView indexView = (IndexView) view;
        // 添加历史记录
        if (filterBox.getValue()==null)
            filterBox.setValue("");
        if (!filterBox.getValue().equals(""))
            FileHandle.AddLine(SettingProperty.filterHistory, filterBox.getValue());
        ViewHandle.InitializeFilterComboBox(SettingProperty.filterHistory, filterBox);
        indexView.getFilterProperty().setExpression(filterBox.getValue());
        if (type.equals("capture")) {
            indexView.clearBrowser();
            indexView.StartCapture("apply");
        }
    }
}

