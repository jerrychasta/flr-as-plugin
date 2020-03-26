package com.flr.command;

import com.flr.FlrConstant;
import com.flr.FlrException;
import com.flr.command.util.FlrAssetUtil;
import com.flr.command.util.FlrCodeUtil;
import com.flr.command.util.FlrFileUtil;
import com.flr.command.util.FlrUtil;
import com.flr.logConsole.FlrColoredLogEntity;
import com.flr.logConsole.FlrLogConsole;
import com.flr.messageBox.FlrMessageBox;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import io.flutter.sdk.*;

/**
 * 专有名词简单解释和示例：
 * （详细定义请看 flr-core 项目的文档描述）
 *
 * package_name：flutter工程的package产物的名称，例如“flutter_demo”
 * resource_file：flutter工程的资源文件，例如“lib/assets/images/hot_foot_N.png”、“lib/assets/images/3.0x/hot_foot_N.png”
 * asset：flutter工程的package产物中资源，可当作是工程中的资源文件的映射和声明，例如上述2个资源对于的asset都是“packages/flutter_demo/assets/images/hot_foot_N.png”
 * file_basename：资源的文件名，其定义是“#{file_basename_no_extension}#{file_extname}”，例如“hot_foot_N.png”
 * file_basename_no_extension：资源的不带扩展名的文件名，例如“hot_foot_N”
 * file_extname：资源的扩展名，例如“.png”
 *
 * asset_name：main asset的名称，例如“assets/images/hot_foot_N.png”
 * asset_id：资源ID，其值一般为 file_basename_no_extension
 * */

// 处理泛型对象时，若不做检查，就进行类型强制转换（如 (Map<String, Object>)map.get("key")），
// 编译器会报警告：FlrCommand.java使用了未经检查或不安全的操作。
// 解决办法之一是，添加 @SuppressWarnings("unchecked")。
@SuppressWarnings("unchecked")
public class FlrCommand implements Disposable {

    public boolean isMonitoringAssets = false;

    private final Project curProject;

    private FlrListener curFlrListener;

    private String messageBoxTitle = "Flr";

    private FlrLogConsole.LogType titleLogType = FlrLogConsole.LogType.tips;

    public FlrCommand(Project project) {
        curProject = project;
    }

    @Override
    public void dispose() {
        if(curFlrListener != null) {
            curFlrListener.dispose();
            curFlrListener = null;
        }
    }

    // MARK: Command Action Methods

    /*
    * 对 flutter 工程进行初始化
    *  */
    public void init(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Init]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrExceptionTitle = "[x]: init failed !!!";

        String flutterProjectRootDir = curProject.getBasePath();

        // ----- Step-1 Begin -----
        // 进行环境检测:
        //  - 检测当前 flutter 工程根目录是否存在 pubspec.yaml
        //
        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterProjectRootDir);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return;
        }

        // ----- Step-1 End -----

        String pubspecFilePath = getPubspecFilePath();
        File pubspecFile = new File(pubspecFilePath);

        // ----- Step-2 Begin -----
        // 添加 flr_config 和 r_dart_library 的依赖声明到 pubspec.yaml
        //

        // 读取pubspec.yaml，然后添加相关配置
        Map<String, Object> pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(pubspecFile);
        if(pubspecConfig == null || (pubspecConfig instanceof Map) == false) {
            flrLogConsole.println(String.format("[x]: %s is a bad YAML file", pubspecFilePath), FlrLogConsole.LogType.error);
            flrLogConsole.println("[*]: please make sure the pubspec.yaml is right", FlrLogConsole.LogType.tips);

            handleFlrException(flrExceptionTitle, FlrException.ILLEGAL_ENV);
            return;
        }

        indicatorMessage = String.format("init %s now ...", curProject.getBasePath());
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 添加 flr_config 到 pubspec.yaml
        //
        // flr_config:
        //
        // flr:
        //    - core_version: 1.0.0
        //    - assets: []
        //    - fonts: []
        //
        Map<String, Object> flrConfig = new LinkedHashMap<>();
        String usedFlrCoreLogicVersion = FlrConstant.CORE_VERSION;
        flrConfig.put("core_version", usedFlrCoreLogicVersion);
        List<String> assetResourceDirList = new ArrayList<String>();
        flrConfig.put("assets", assetResourceDirList);
        List<String> fontResourceDirList = new ArrayList<String>();
        flrConfig.put("fonts", fontResourceDirList);
        pubspecConfig.put("flr", flrConfig);

        indicatorMessage = "add flr configuration into pubspec.yaml done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // 添加依赖包`r_dart_library`(https://github.com/YK-Unit/r_dart_library)的声明到pubspec.yaml
        String rDartLibraryVersion = getRDartLibraryVersion();
        Map<String, Object> rDartLibraryMap = new LinkedHashMap<String, Object>();
        Map<String, String> rDartLibraryGitMap = new LinkedHashMap<String, String>();
        rDartLibraryGitMap.put("url", "https://github.com/YK-Unit/r_dart_library.git");
        rDartLibraryGitMap.put("ref", rDartLibraryVersion);
        rDartLibraryMap.put("git", rDartLibraryGitMap);

        Map<String, Object> dependenciesMap = (Map<String, Object>)pubspecConfig.get("dependencies");
        dependenciesMap.put("r_dart_library", rDartLibraryMap);
        pubspecConfig.put("dependencies", dependenciesMap);

        // ----- Step-2 End -----

        indicatorMessage = "add dependency \"r_dart_library\"(https://github.com/YK-Unit/r_dart_library) into pubspec.yaml done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-3 Begin -----
        // 对Flutter配置进行修正，以避免执行获取依赖操作时会失败：
        // - 检测Flutter配置中的assets选项是否是一个非空数组；若不是，则删除assets选项；
        // - 检测Flutter配置中的fonts选项是否是一个非空数组；若不是，则删除fonts选项。
        //

        Map<String, Object> flutterConfig = (Map<String, Object>)pubspecConfig.get("flutter");
        String flutterAssetsKey = "assets";
        Object flutterAssets = flutterConfig.get(flutterAssetsKey);
        Boolean shouldRmFlutterAssetsKey = true;
        if(flutterAssets instanceof List && ((List)flutterAssets).isEmpty() == false) {
            shouldRmFlutterAssetsKey = false;
        }
        if(shouldRmFlutterAssetsKey) {
            flutterConfig.remove(flutterAssetsKey);
        }

        // 检测 flutter 下的fonts配置是否有效（fonts要求为非空数组），若无效，则删除该配置，避免执行 flutter pub get 时会失败
        String flutterFontsKey = "fonts";
        Object flutterFonts = flutterConfig.get(flutterAssetsKey);
        Boolean shouldRmFlutterFontsKey = true;
        if(flutterFonts instanceof List && ((List)flutterFonts).isEmpty() == false) {
            shouldRmFlutterFontsKey = false;
        }
        if(shouldRmFlutterFontsKey) {
            flutterConfig.remove(flutterFontsKey);
        }

        pubspecConfig.put("flutter", flutterConfig);
        // ----- Step-3 End -----

        // 保存并刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(pubspecConfig, pubspecFile);

        // ----- Step-4 Begin -----
        // 调用flutter工具，为flutter工程获取依赖
        //

        indicatorMessage = "get dependency \"r_dart_library\" via running \"Flutter Packages Get\" action now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        indicatorMessage = "get dependency \"r_dart_library\" done!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-4 End -----

        indicatorMessage = "[√]: init done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String contentTitle = "[√]: init done !!!";
        showSuccessMessage(contentTitle, "", false);
    }

    /*
    * 扫描资源目录，自动为资源添加声明到 pubspec.yaml 和生成 r.g.dart
    * */
    public void generate(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Generate]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrExceptionTitle = "[x]: generate failed !!!";

        // 警告日志数组
        List<FlrColoredLogEntity> warningMessages = new ArrayList<FlrColoredLogEntity>();

        String flutterProjectRootDir = curProject.getBasePath();
        String pubspecFilePath;
        File pubspecFile;
        Map<String, Object> pubspecConfig;
        Map<String, Object> flrConfig;
        List<List<String>> resourceDirResultTuple;

        // ----- Step-1 Begin -----
        // 进行环境检测；若发现不合法的环境，则抛出异常，终止当前进程：
        // - 检测当前flutter工程根目录是否存在pubspec.yaml
        // - 检测当前pubspec.yaml中是否存在Flr的配置
        // - 检测当前flr_config中的resource_dir配置是否合法：
        //    判断合法的标准是：assets配置或者fonts配置了至少1个legal_resource_dir
        //

        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterProjectRootDir);

            pubspecFilePath = getPubspecFilePath();
            pubspecFile = new File(pubspecFilePath);
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(pubspecFile);

            FlrChecker.checkFlrConfigIsExisted(flrLogConsole, pubspecConfig);
            flrConfig = (Map<String, Object>)pubspecConfig.get("flr");

            resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(flrLogConsole, flrConfig, flutterProjectRootDir);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return;
        }

        String packageName = (String) pubspecConfig.get("name");

        // ----- Step-1 End -----

        // ----- Step-2 Begin -----
        // 进行核心逻辑版本检测：
        // 检测Flr配置中的核心逻辑版本号和当前工具的核心逻辑版本号是否一致；若不一致，则生成“核心逻辑版本不一致”的警告日志，存放到警告日志数组

        String flrCoreVersion = (String)flrConfig.get("core_version");

        if(flrCoreVersion instanceof String == false) {
            flrCoreVersion = "unknown";
        }

        if(flrCoreVersion.equals(FlrConstant.CORE_VERSION) == false) {
            String warningText = String.format("[!]: warning, the core logic version of the configured Flr tool is %s, while the core logic version of the currently used Flr tool is %s", flrCoreVersion,FlrConstant.CORE_VERSION);
            String tipsText = "[*]: to fix it, you should make sure that the core logic version of the Flr tool you are currently using is consistent with the configuration";

            FlrColoredLogEntity.Item warningItem = new FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning);
            FlrColoredLogEntity.Item tipsItem = new FlrColoredLogEntity.Item(tipsText, FlrLogConsole.LogType.tips);
            List<FlrColoredLogEntity.Item> items = Arrays.asList(warningItem, tipsItem);

            FlrColoredLogEntity logEntity = new FlrColoredLogEntity(items);
            warningMessages.add(logEntity);
        }

        // ----- Step-2 End -----

        // ----- Step-3 Begin -----
        // 获取assets_legal_resource_dir数组、fonts_legal_resource_dir数组和illegal_resource_dir数组：
        // - 从flr_config中的assets配置获取assets_legal_resource_dir数组和assets_illegal_resource_dir数组；
        // - 从flr_config中的fonts配置获取fonts_legal_resource_dir数组和fonts_illegal_resource_dir数组；
        // - 合并assets_illegal_resource_dir数组和fonts_illegal_resource_dir数组为illegal_resource_dir数组‘；若illegal_resource_dir数组长度大于0，则生成“存在非法的资源目录”的警告日志，存放到警告日志数组。

        // 合法的资源目录数组
        List<String> assetsLegalResourceDirArray = resourceDirResultTuple.get(0);
        List<String> fontsLegalResourceDirArray = resourceDirResultTuple.get(1);
        // 非法的资源目录数组
        List<String>  illegalResourceDirArray = resourceDirResultTuple.get(2);

        if(illegalResourceDirArray.size() > 0) {
            String warningText = "[!]: warning, found the following resource directory which is not existed: ";
            for (String resourceDir : illegalResourceDirArray) {
                warningText += "\n" + String.format("  - %s", resourceDir);
            }

            FlrColoredLogEntity.Item warningItem = new FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning);
            List<FlrColoredLogEntity.Item> items = Arrays.asList(warningItem);

            FlrColoredLogEntity logEntity = new FlrColoredLogEntity(items);
            warningMessages.add(logEntity);
        }

        // ----- Step-3 End -----

        indicatorMessage = "scan assets now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-4 Begin -----
        // 扫描assets_legal_resource_dir数组中的legal_resource_dir，输出image_asset数组和illegal_image_file数组：
        // - 创建image_asset数组、illegal_image_file数组；
        // - 遍历assets_legal_resource_dir数组，按照如下处理每个资源目录：
        //  - 扫描当前资源目录和其第1级的子目录，查找所有image_file；
        //  - 根据legal_resource_file的标准，筛选查找结果生成legal_image_file子数组和illegal_image_file子数组；illegal_image_file子数组合并到illegal_image_file数组；
        //  - 根据image_asset的定义，遍历legal_image_file子数组，生成image_asset子数；组；image_asset子数组合并到image_asset数组。
        // - 对image_asset数组做去重处理；
        // - 按照字典顺序对image_asset数组做升序排列（一般使用开发语言提供的默认的sort算法即可）；
        // - 输出image_asset数组和illegal_image_file数组。
        //

        List<String> imageAssetArray = new ArrayList<String>();
        List<VirtualFile> illegalImageFileArray = new ArrayList<VirtualFile>();

        for (String resourceDir : assetsLegalResourceDirArray) {
            List<List<VirtualFile>> imageFileResultTuple = FlrFileUtil.findImageFiles(curProject, resourceDir);
            List<VirtualFile> legalImageFileSubArray = imageFileResultTuple.get(0);
            List<VirtualFile> illegalImageFileSubArray = imageFileResultTuple.get(1);

            illegalImageFileArray.addAll(illegalImageFileSubArray);

            List<String> imageAssetSubArray = FlrAssetUtil.generateImageAssets(legalImageFileSubArray, flutterProjectRootDir, resourceDir, packageName);
            imageAssetArray.addAll(imageAssetSubArray);
        }

        // uniq
        imageAssetArray = new ArrayList<String>(new HashSet<String>(imageAssetArray));
        // sort
        Collections.sort(imageAssetArray);

        // ----- Step-4 End -----

        // ----- Step-5 Begin -----
        // 扫描assets_legal_resource_dir数组中的legal_resource_dir，输出text_asset数组和illegal_text_file数组：
        // - 创建text_asset数组、illegal_text_file数组；
        // - 遍历assets_legal_resource_dir数组，按照如下处理每个资源目录：
        //  - 扫描当前资源目录和其所有层级的子目录，查找所有text_file；
        //  - 根据legal_resource_file的标准，筛选查找结果生成legal_text_file子数组和illegal_text_file子数组；illegal_text_file子数组合并到illegal_text_file数组；
        //  - 根据text_asset的定义，遍历legal_text_file子数组，生成text_asset子数组；text_asset子数组合并到text_asset数组。
        // - 对text_asset数组做去重处理；
        // - 按照字典顺序对text_asset数组做升序排列（一般使用开发语言提供的默认的sort算法即可）；
        // - 输出text_asset数组和illegal_image_file数组。
        //

        List<String> textAssetArray = new ArrayList<String>();
        List<VirtualFile> illegalTextFileArray = new ArrayList<VirtualFile>();

        for (String resourceDir : assetsLegalResourceDirArray) {
            List<List<VirtualFile>> textFileResultTuple = FlrFileUtil.findTextFiles(curProject, resourceDir);
            List<VirtualFile> legalTextFileSubArray = textFileResultTuple.get(0);
            List<VirtualFile> illegalTextFileSubArray = textFileResultTuple.get(1);

            illegalTextFileArray.addAll(illegalTextFileSubArray);

            List<String> textAssetSubArray = FlrAssetUtil.generateTextAssets(legalTextFileSubArray, flutterProjectRootDir, resourceDir, packageName);
            textAssetArray.addAll(textAssetSubArray);
        }

        // uniq
        textAssetArray = new ArrayList<String>(new HashSet<String>(textAssetArray));
        // sort
        Collections.sort(textAssetArray);

        // ----- Step-5 End -----

        // ----- Step-6 Begin -----
        // 扫描fonts_legal_resource_dir数组中的legal_resource_dir，输出font_family_config数组、illegal_font_file数组：
        // - 创建font_family_config数组、illegal_font_file数组；
        // - 遍历fonts_legal_resource_dir数组，按照如下处理每个资源目录：
        //  - 扫描当前资源目录，获得其第1级子目录数组，并按照字典顺序对数组做升序排列（一般使用开发语言提供的默认的sort算法即可）；
        //  - 遍历第1级子目录数组，按照如下处理每个子目录：
        //    - 获取当前子目录的名称，生成font_family_name；
        //    - 扫描当前子目录和其所有子目录，查找所有font_file；
        //    - 根据legal_resource_file的标准，筛选查找结果生成legal_font_file数组和illegal_font_file子数组；illegal_font_file子数组合并到illegal_font_file数组；
        //    - 据font_asset的定义，遍历legal_font_file数组，生成font_asset_config数组；
        //    - 按照字典顺序对生成font_asset_config数组做升序排列（比较asset的值）；
        //    - 根据font_family_config的定义，为当前子目录组织font_family_name和font_asset_config数组生成font_family_config对象，添加到font_family_config子数组；font_family_config子数组合并到font_family_config数组。
        // - 输出font_family_config数组、illegal_font_file数组；
        // - 按照字典顺序对font_family_config数组做升序排列（比较family的值）。


        List<Map> fontFamilyConfigArray = new ArrayList<Map>();
        List<VirtualFile> illegalFontFileArray = new ArrayList<VirtualFile>();

        for (String resourceDir : fontsLegalResourceDirArray) {
            List<VirtualFile> fontFamilyDirArray = FlrFileUtil.findTopChildDirs(curProject, resourceDir);

            for (VirtualFile fontFamilyDirFile : fontFamilyDirArray) {
               String fontFamilyName = fontFamilyDirFile.getName();

                List<List<VirtualFile>> fontFileResultTuple = FlrFileUtil.findFontFilesInFontFamilyDir(curProject, fontFamilyDirFile);
                List<VirtualFile> legalFontFileArray = fontFileResultTuple.get(0);
                List<VirtualFile> illegalFontFileSubArray = fontFileResultTuple.get(1);

                illegalFontFileArray.addAll(illegalFontFileSubArray);

                if(legalFontFileArray.size() <= 0) {
                    continue;
                }

                List<Map> fontAssetConfigArray = FlrAssetUtil.generateFontAssetConfigs(legalFontFileArray, flutterProjectRootDir, resourceDir, packageName);
                fontAssetConfigArray.sort(new Comparator<Map>() {
                    @Override
                    public int compare(Map o1, Map o2) {
                        String assetValue1 = (String)o1.get("asset");
                        String assetValue2 = (String)o2.get("asset");
                        return assetValue1.compareTo(assetValue2);
                    }
                });

                Map<String, Object> fontFamilyConfig = new LinkedHashMap<String, Object>();
                fontFamilyConfig.put("family", fontFamilyName);
                fontFamilyConfig.put("fonts", fontAssetConfigArray);

                fontFamilyConfigArray.add(fontFamilyConfig);
            }
        }

        fontFamilyConfigArray.sort(new Comparator<Map>() {
            @Override
            public int compare(Map o1, Map o2) {
                String familyValue1 = (String)o1.get("family");
                String familyValue2 = (String)o2.get("family");
                return familyValue1.compareTo(familyValue2);
            }
        });

        // ----- Step-6 End -----

        indicatorMessage = "scan assets done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        //  ----- Step-7 Begin -----
        //  检测是否存在illegal_resource_file：
        // - 合并illegal_image_file数组、illegal_text_file数组和illegal_font_file数组为illegal_resource_file数组；
        // - 若illegal_resource_file数组长度大于0，则生成“存在非法的资源文件”的警告日志，存放到警告日志数组。

        List<VirtualFile> illegalResourceFileArray = new ArrayList<VirtualFile>();
        illegalResourceFileArray.addAll(illegalImageFileArray);
        illegalResourceFileArray.addAll(illegalTextFileArray);
        illegalResourceFileArray.addAll(illegalFontFileArray);

        if(illegalResourceFileArray.size() > 0) {
            String warningText = "[!]: warning, found the following illegal resource file who's file basename contains illegal characters: ";
            for (VirtualFile resourceFile : illegalResourceFileArray) {
                warningText += "\n" + String.format("  - %s", resourceFile.getPath());
            }

            String tipsText  = "[*]: to fix it, you should only use letters (a-z, A-Z), numbers (0-9), and the other legal characters ('_', '+', '-', '.', '·', '!', '@', '&', '$', '￥') to name the file";

            FlrColoredLogEntity.Item warningItem = new FlrColoredLogEntity.Item(warningText, FlrLogConsole.LogType.warning);
            FlrColoredLogEntity.Item tipsItem = new FlrColoredLogEntity.Item(tipsText, FlrLogConsole.LogType.tips);
            List<FlrColoredLogEntity.Item> items = Arrays.asList(warningItem, tipsItem);

            FlrColoredLogEntity logEntity = new FlrColoredLogEntity(items);
            warningMessages.add(logEntity);
        }

        // ----- Step-7 End -----

        // 添加资源声明到 `pubspec.yaml`
        indicatorMessage = "specify scanned assets in pubspec.yaml now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-8 Begin -----
        // 为扫描得到的legal_resource_file添加资源声明到pubspec.yaml：
        // - 合并image_asset数组和text_asset数组为asset数组（image_asset数组元素在前）;
        // - 修改pubspec.yaml中flutter-assets配置的值为asset数组；
        // - 修改pubspec.yaml中flutter-fonts配置的值为font_family_config数组。
        //
        List<String> assetArray = new ArrayList<>();
        assetArray.addAll(imageAssetArray);
        assetArray.addAll(textAssetArray);

        Map<String, Object> flutterConfig = (Map<String, Object>)pubspecConfig.get("flutter");
        if(assetArray.size() > 0) {
            flutterConfig.put("assets", assetArray);
        } else {
            flutterConfig.remove("assets");
        }

        if(fontFamilyConfigArray.size() > 0) {
            flutterConfig.put("fonts", fontFamilyConfigArray);
        } else {
            flutterConfig.remove("fonts");
        }
        pubspecConfig.put("flutter", flutterConfig);

        // 保存刷新 pubspec.yaml
        FlrFileUtil.dumpPubspecConfigToFile(pubspecConfig, pubspecFile);

        // ----- Step-8 End -----

        indicatorMessage = "specify scanned assets in pubspec.yaml done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-9 Begin -----
        // 按照SVG分类，从image_asset数组筛选得到有序的non_svg_image_asset数组和svg_image_asset数组：
        //  - 按照SVG分类，从image_asset数组筛选得到non_svg_image_asset数组和svg_image_asset数组；
        //  - 按照字典顺序对non_svg_image_asset数组和svg_image_asset数组做升序排列（一般使用开发语言提供的默认的sort算法即可）；
        //

        List<String> nonSvgImageAssetArray = new ArrayList<String>();
        List<String> svgImageAssetArray = new ArrayList<String>();

        for (String asset : imageAssetArray) {
            File assetFile = new File(asset);
            String fileExtName = FlrFileUtil.getFileExtension(assetFile).toLowerCase();

            if(fileExtName.equals(".svg")) {
                svgImageAssetArray.add(asset);
            } else {
                nonSvgImageAssetArray.add(asset);
            }
        }

        Collections.sort(nonSvgImageAssetArray);
        Collections.sort(svgImageAssetArray);

        // ----- Step-9 End -----

        // 创建生成 `r.g.dart`
        indicatorMessage = "generate \"r.g.dart\" now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);


        // ----- Step-10 Begin -----
        // 在当前根目录下创建新的r.g.dart文件。
        //

        String r_dart_file_content = "";

        // ----- Step-10 End -----

        // ----- Step-11 Begin -----
        // 生成 R 类的代码，追加写入r.g.dart
        //

        String g_R_class_code = FlrCodeUtil.generate_R_class(packageName);
        r_dart_file_content += g_R_class_code;

        // ----- Step-11 End -----


        // ----- Step-12 Begin -----
        // 生成 AssetResource 类的代码，追加写入r.g.dart
        //

        r_dart_file_content += "\n";
        String g_AssetResource_class_code = FlrCodeUtil.generate_AssetResource_class(packageName);
        r_dart_file_content += g_AssetResource_class_code;

        // ----- Step-12 End -----


        // ----- Step-13 Begin -----
        // 遍历 non_svg_image_asset 数组，生成 _R_Image_AssetResource 类，追加写入 r.g.dart
        //

        r_dart_file_content += "\n";
        String g__R_Image_AssetResource_class_code = FlrCodeUtil.generate__R_Image_AssetResource_class(nonSvgImageAssetArray, packageName);
        r_dart_file_content += g__R_Image_AssetResource_class_code;

        // ----- Step-13 End -----


        // ----- Step-14 Begin -----
        // 遍历 svg_image_asset 数组，生成 _R_Svg_AssetResource 类，追加写入 r.g.dart。
        //

        r_dart_file_content += "\n";
        String g__R_Svg_AssetResource_class_code = FlrCodeUtil.generate__R_Svg_AssetResource_class(svgImageAssetArray, packageName);
        r_dart_file_content += g__R_Svg_AssetResource_class_code;

        // ----- Step-14 End -----

        // ----- Step-15 Begin -----
        // 遍历 text_asset 数组，生成 _R_Image_AssetResource 类，追加写入 r.g.dart
        //

        r_dart_file_content += "\n";
        String g__R_Text_AssetResource_class_code = FlrCodeUtil.generate__R_Text_AssetResource_class(textAssetArray, packageName);
        r_dart_file_content += g__R_Text_AssetResource_class_code;

        // ----- Step-15 End -----

        // ----- Step-16 Begin -----
        // 遍历non_svg_image_asset数组，生成 _R_Image 类，追加写入 r.g.dart
        //

        r_dart_file_content += "\n";
        String g__R_Image_class_code = FlrCodeUtil.generate__R_Image_class(nonSvgImageAssetArray, packageName);
        r_dart_file_content += g__R_Image_class_code;

        // ----- Step-16 End -----

        // ----- Step-17 Begin -----
        // 遍历 svg_image_asset 数组，生成 _R_Svg 类，追加写入 r.g.dart。
        //

        r_dart_file_content += "\n";
        String g__R_Svg_class_code = FlrCodeUtil.generate__R_Svg_class(svgImageAssetArray, packageName);
        r_dart_file_content += g__R_Svg_class_code;

        // ----- Step-17 End -----

        // ----- Step-18 Begin -----
        // 遍历 text_asset 数组，生成 _R_Image 类，追加写入 r.g.dart。
        //

        r_dart_file_content += "\n";
        String g__R_Text_class_code = FlrCodeUtil.generate__R_Text_class(textAssetArray, packageName);
        r_dart_file_content += g__R_Text_class_code;

        // ----- Step-18 End -----

        // ----- Step-19 Begin -----
        // 遍历font_family_config数组，根据下面的模板生成_R_Font_Family类，追加写入r.g.dart。

        r_dart_file_content += "\n";
        String g__R_Font_Family_class_code = FlrCodeUtil.generate__R_FontFamily_class(fontFamilyConfigArray, packageName);
        r_dart_file_content += g__R_Font_Family_class_code;

        // ----- Step-19 End -----


        // ----- Step-20 Begin -----
        // 结束操作，保存 r.g.dart
        //

        // 把 rDartContent 写到 r.g.dart 中
        String rDartFilePath = flutterProjectRootDir + "/lib/r.g.dart";
        File rDartFile = new File(rDartFilePath);
        try {
            FlrFileUtil.writeContentToFile(curProject, r_dart_file_content, rDartFile);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return;
        }
        // ----- Step-20 End -----

        indicatorMessage = String.format("generate for %s done!", curProject.getBasePath());
        flrLogConsole.println(indicatorMessage, indicatorType);


        // ----- Step-21 Begin -----
        // 调用 flutter 工具对 r.g.dart 进行格式化操作
        //

        // 格式化 r.g.dart
        indicatorMessage = "format r.g.dart now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.formatDartFile(curProject, rDartFile);
        indicatorMessage = "format r.g.dart done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-21 End -----

        // ----- Step-22 Begin -----
        // 调用flutter工具，为flutter工程获取依赖
        //

        // 执行 "Flutter Packages Get" action
        indicatorMessage = "running \"Flutter Packages Get\" action now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        FlrUtil.runFlutterPubGet(actionEvent);
        indicatorMessage = "running \"Flutter Packages Get\" action done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-22 End -----

        indicatorMessage = "[√]: generate done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        // ----- Step-23 Begin -----
        // 判断警告日志数组是否为空，若不为空，输出所有警告日志
        //

        int warningCount = warningMessages.size();
        if(warningCount > 0) {
            for (FlrColoredLogEntity coloredLogEntity : warningMessages) {
                flrLogConsole.println(coloredLogEntity);
            }
        }

        String contentTitle = "[√]: generate done !!!";
        if(warningCount > 0) {
            String warningUnitDesc = "warning";
            if(warningCount > 1) {
                warningUnitDesc = "warnings";
            }
            String warningMessage = String.format("[!]: found %d %s, you can get the details from Flr ToolWindow", warningCount, warningUnitDesc);
            showSuccessMessage(contentTitle, warningMessage, true);
        } else {
            showSuccessMessage(contentTitle, "", false);
        }
    }

    /*
    * 启动一个资源变化监控服务，若检测到有资源变化，就自动执行generate操作
    * */
    public Boolean startMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Start Monitor]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        String flrExceptionTitle = "[x]: generate failed !!!";

        String flutterProjectRootDir = curProject.getBasePath();
        String pubspecFilePath;
        File pubspecFile;
        Map<String, Object> pubspecConfig;
        Map<String, Object> flrConfig;
        List<List<String>> resourceDirResultTuple;

        // ----- Step-1 Begin -----
        // 进行环境检测；若发现不合法的环境，则抛出异常，终止当前进程：
        // - 检测当前flutter工程根目录是否存在pubspec.yaml
        // - 检测当前pubspec.yaml中是否存在Flr的配置
        // - 检测当前flr_config中的resource_dir配置是否合法：
        //    判断合法的标准是：assets配置或者fonts配置了至少1个legal_resource_dir
        //

        try {
            FlrChecker.checkPubspecFileIsExisted(flrLogConsole, flutterProjectRootDir);

            pubspecFilePath = getPubspecFilePath();
            pubspecFile = new File(pubspecFilePath);
            pubspecConfig = FlrFileUtil.loadPubspecConfigFromFile(pubspecFile);

            FlrChecker.checkFlrConfigIsExisted(flrLogConsole, pubspecConfig);
            flrConfig = (Map<String, Object>)pubspecConfig.get("flr");

            resourceDirResultTuple = FlrChecker.checkFlrAssetsIsLegal(flrLogConsole, flrConfig, flutterProjectRootDir);
        } catch (FlrException e) {
            handleFlrException(flrExceptionTitle, e);
            return false;
        }

        // ----- Step-1 End -----

        String packageName = (String) pubspecConfig.get("name");

        if(curFlrListener != null) {
            stopMonitor(actionEvent, flrLogConsole);
        }

        // ----- Step-2 Begin -----
        // 执行一次 flr generate 操作
        //

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String nowStr = df.format(new Date());// new Date()为获取当前系统时间，也可使用当前时间戳
        indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);
        generate(actionEvent, flrLogConsole);
        flrLogConsole.println("", indicatorType);
        indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" done!";
        flrLogConsole.println(indicatorMessage, indicatorType);
        indicatorMessage = "---------------------------------------------------------------------------------";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);

        // ----- Step-2 End -----

        // ----- Step-3 Begin -----
        // 获取legal_resource_dir数组：
        // - 从flr_config中的assets配置获取assets_legal_resource_dir数组；
        // - 从flr_config中的fonts配置获取fonts_legal_resource_dir数组；
        // - 合并assets_legal_resource_dir数组和fonts_legal_resource_dir数组为legal_resource_dir数组。
        //

        // 合法的资源目录数组
        List<String> assetsLegalResourceDirArray = resourceDirResultTuple.get(0);
        List<String> fontsLegalResourceDirArray = resourceDirResultTuple.get(1);
        List<String> legalResourceDirArray = new ArrayList<String>();
        legalResourceDirArray.addAll(assetsLegalResourceDirArray);
        legalResourceDirArray.addAll(fontsLegalResourceDirArray);
        // 非法的资源目录数组
        List<String> illegalResourceDirArray = resourceDirResultTuple.get(2);

        // ----- Step-3 End -----

        // ----- Step-4 Begin -----
        // 启动资源监控服务
        //  - 启动一个文件监控服务，对 legal_resource_dir 数组中的资源目录进行文件监控
        //  - 若服务检测到资源变化（资源目录下的发生增/删/改文件），则执行一次 flr generate 操作
        //

        nowStr = df.format(new Date());
        indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "launch a monitoring service now ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "launching ...";
        flrLogConsole.println(indicatorMessage, indicatorType);

        FlrListener.AssetChangesEventCallback assetChangesEventCallback = new FlrListener.AssetChangesEventCallback() {
            @Override
            public void run() {
                String contentTitle = "[!]: detect some asset changes !!!";
                String contentMessage = "[*]: invoke Flr-Generate Action now ... ";
                showSuccessMessage(contentTitle, contentMessage, false);

                String indicatorMessage = "";
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String nowStr = df.format(new Date());

                indicatorMessage = String.format("--------------------------- %s ---------------------------", nowStr);
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = "detect some asset changes, run Flr-Generate Action now";
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" now ...";
                flrLogConsole.println(indicatorMessage, indicatorType);

                flrLogConsole.println("", indicatorType);
                generate(actionEvent, flrLogConsole);

                flrLogConsole.println("", indicatorType);
                indicatorMessage = "scan assets, specify scanned assets in pubspec.yaml, generate \"r.g.dart\" done!";
                flrLogConsole.println(indicatorMessage, indicatorType);

                indicatorMessage = "---------------------------------------------------------------------------------";
                flrLogConsole.println(indicatorMessage, indicatorType);
                flrLogConsole.println("", indicatorType);

                indicatorMessage =
                        "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                                "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";
                flrLogConsole.println(indicatorMessage, indicatorType);

                contentTitle = "[!]: invoke Flr-Generate Action done !!!";
                contentMessage = "[*]: you can get the details from Flr ToolWindow";
                showSuccessMessage(contentTitle, contentMessage, false);
            }
        };
        curFlrListener = new FlrListener(curProject, legalResourceDirArray, assetChangesEventCallback);
        isMonitoringAssets = true;

        indicatorMessage = "launch a monitoring service done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        indicatorMessage = "the monitoring service is monitoring the following resource directory:";
        flrLogConsole.println(indicatorMessage, indicatorType);

        for(String resourceDir: legalResourceDirArray) {
            indicatorMessage = String.format("  - %s", resourceDir);
            flrLogConsole.println(indicatorMessage, indicatorType);
        }

        if(illegalResourceDirArray.size() > 0) {
            indicatorMessage = "";
            flrLogConsole.println(indicatorMessage, indicatorType);

            indicatorMessage = "[!]: warning, found the following resource directory which is not existed: ";
            flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.warning);

            for (String resourceDir : illegalResourceDirArray) {
                indicatorMessage = String.format("  - %s", resourceDir);
                flrLogConsole.println(indicatorMessage, FlrLogConsole.LogType.warning);
            }
        }
        indicatorMessage = "---------------------------------------------------------------------------------";
        flrLogConsole.println(indicatorMessage, indicatorType);
        flrLogConsole.println("", indicatorType);

        indicatorMessage =
                "[*]: the monitoring service is monitoring the asset changes, and then auto scan assets, specifies assets and generates \"r.g.dart\" ...\n" +
                        "[*]: you can click menu \"Tools-Flr-Stop Monitor\" to terminate it\n";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String contentTitle = "[√]: start monitor done !!!";
        String contentMessage = "[*]: you can get the details from Flr ToolWindow";
        showSuccessMessage(contentTitle, contentMessage, false);

        return true;
    }

    /*
    * 停止资源变化监控服务
    * */
    public void stopMonitor(@NotNull AnActionEvent actionEvent, @NotNull FlrLogConsole flrLogConsole) {
        String indicatorMessage = "[Flr Stop Monitor]";
        FlrLogConsole.LogType indicatorType = FlrLogConsole.LogType.normal;
        flrLogConsole.println(indicatorMessage, titleLogType);

        if(curFlrListener != null) {
            curFlrListener.dispose();
            curFlrListener = null;
        }
        isMonitoringAssets = false;

        indicatorMessage = "[√]: terminate the monitoring service done !!!";
        flrLogConsole.println(indicatorMessage, indicatorType);

        String contentTitle = "[√]: stop monitor done !!!";
        String contentMessage = "[*]: you can get the details from Flr ToolWindow";
        showSuccessMessage(contentTitle, contentMessage, false);
    }

    // MARK: pubspec.yaml Util Methods

    /*
    * get the path of pubspec.yaml
    * */
    private String getPubspecFilePath() {
        String flutterProjectRootDir = curProject.getBasePath();
        String pubspecFilePath = flutterProjectRootDir + "/pubspec.yaml";
        return pubspecFilePath;
    }

    /*
     * get the right version of r_dart_library package based on flutter's version
     * to get more detail, see https://github.com/YK-Unit/r_dart_library#dependency-relationship-table
     * */
    private String getRDartLibraryVersion() {
        String rDartLibraryVersion = "0.1.1";

        VirtualFile flutterSdkHome = FlutterSdk.getFlutterSdk(curProject).getHome();
        FlutterSdkVersion flutterSdkVersion = FlutterSdkVersion.readFromSdk(flutterSdkHome);

        String flutterVersionWithoutHotfixStr = flutterSdkVersion.toString();
        int compareResult = FlrUtil.versionCompare(flutterVersionWithoutHotfixStr, "1.10.15");
        if(compareResult == 0 || compareResult == 1) {
            rDartLibraryVersion = "0.2.1";
        }

        return rDartLibraryVersion;
    }

    // MARK: Exception Handler Methods

    private void handleFlrException(@NotNull String contentTitle, FlrException exception) {
        String contentMessage = exception.getMessage();
        showFailureMessage(contentTitle, contentMessage);
    }

    // MARK: MessageBox Show Methods

    private void showSuccessMessage(@NotNull String title, @NotNull String message, Boolean hasWarning) {
        String content = "<p>" + title + "</p>" + "<p>" + message + "</p>";
        if(hasWarning) {
            FlrMessageBox.showWarning(curProject, messageBoxTitle, content);
        } else {
            FlrMessageBox.showInfo(curProject, messageBoxTitle, content);
        }
    }

    private void showFailureMessage(@NotNull String title, @NotNull String message) {
        String content = "<p>" + title + "</p>" + "<p>" + message + "</p>";
        FlrMessageBox.showError(curProject, messageBoxTitle, content);
    }

}
