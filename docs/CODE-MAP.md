# 代码地图（自动生成，勿手改）

`python tools/map.py` 重新生成；`python tools/map.py --check` 校验是否过期。

| 文件 | 行数 | 主要声明 |
|---|---|---|
| `MainActivity.kt` | 1454 | `androidx`、`AppRoot`、`PlaceholderScreen`、`sampleRecords`、`samplePlans` |
| `data/ApkDownloader.kt` | 363 |  |
| `data/BackupExport.kt` | 261 |  |
| `data/BackupParser.kt` | 384 | `parseWebDav`、`readDavConfig`、`normalizeDavUrl`、`decPwd` |
| `data/ContextExt.kt` | 21 | `Context` |
| `data/CrashGuard.kt` | 255 |  |
| `data/Daos.kt` | 191 | `buildDatabase` |
| `data/Entities.kt` | 41 |  |
| `data/GallerySaver.kt` | 103 |  |
| `data/Integrity.kt` | 99 |  |
| `data/IntegrityPin.kt` | 18 |  |
| `data/LocalBackup.kt` | 125 |  |
| `data/MemoirHtml.kt` | 235 |  |
| `data/Milestones.kt` | 236 | `itemsOf`、`normalizePeakName`、`normWeather`、`parseRecordDate`、`milestoneStats`、`milestoneProgress`、`milestoneDone`、`milestoneProgressText` |
| `data/MtnMerge.kt` | 115 |  |
| `data/Net.kt` | 38 |  |
| `data/Notifier.kt` | 163 |  |
| `data/Photo.kt` | 159 |  |
| `data/PhotoMaintenance.kt` | 90 |  |
| `data/PlanAlarm.kt` | 196 |  |
| `data/Prefs.kt` | 81 |  |
| `data/RelativeTime.kt` | 32 |  |
| `data/SecretBox.kt` | 101 |  |
| `data/Trace.kt` | 77 |  |
| `data/Updater.kt` | 278 |  |
| `data/WebDavClient.kt` | 491 |  |
| `ui/AppChrome.kt` | 944 | `pillScale`、`GlassBottomBar`、`featherAlpha`、`featherBrush`、`TopGlassBar`、`FpsPill`、`rememberFps`、`rememberFpsStat` |
| `ui/Aurora.kt` | 205 | `AuroraLayer`、`Spot`、`seg`、`smooth` |
| `ui/Confetti.kt` | 115 | `ConfettiOverlay` |
| `ui/Glass.kt` | 123 | `glassSource`、`glassPanel` |
| `ui/GlassOverlay.kt` | 734 | `GlassOverlay`、`GlassOverlayImpl`、`OverlayBody` |
| `ui/GlassPickers.kt` | 515 | `GlassDateTimePicker`、`StepArrow`、`Stepper`、`MoodWeatherPicker`、`PickerField`、`parseToCalendar`、`emojiOf` |
| `ui/GlassWidgets.kt` | 403 | `GlassTextField`、`GlassButton`、`GlassConfirm`、`GlassSegmented`、`FieldLabel`、`DialogTitle`、`DialogMessage`、`DialogCloseBar` |
| `ui/GlobalSearchBar.kt` | 119 | `GlobalSearchBar` |
| `ui/LegalConsent.kt` | 288 | `LegalConsentScreen`、`LegalLinkLine`、`buildCheckText`、`buildStateTitle` |
| `ui/SafeTap.kt` | 147 | `safeClickable`、`safeCombinedClickable` |
| `ui/Toast.kt` | 174 | `AppToastHost` |
| `ui/liquid/LiquidGlass.kt` | 1401 | `LiquidBackdropHost`、`liquidGlassSurface`、`glassBar`、`LocalDark`、`glassPill`、`androidx`、`glassStatic`、`staticGlassSurface` … |
| `ui/milestones/MilestoneListDialog.kt` | 270 | `MilestoneListDialog`、`MilestoneRow`、`MilestoneEntryCard` |
| `ui/overview/HeatmapView.kt` | 449 | `HeatmapCard`、`DayRecordRow`、`PickChip`、`fmt1`、`fmtDuration` |
| `ui/overview/OverviewScreen.kt` | 1049 | `OverviewScreen`、`YMChip`、`GuideLine`、`findTimeMachine`、`StatCard`、`fmtH`、`MiniStat`、`SoftErrChip` |
| `ui/overview/YearReviewDialog.kt` | 487 | `yearRecordsOf`、`ymd`、`calcYearStats`、`YearReviewDialog`、`MetricCard`、`durNum`、`durUnit`、`pctChange` … |
| `ui/overview/YearReviewImage.kt` | 183 |  |
| `ui/photos/PhotoLightbox.kt` | 617 | `PhotoLightbox`、`ZoomableImage`、`CircleButton`、`LightboxPill`、`shareOne`、`saveToGallery` |
| `ui/photos/Photos.kt` | 149 | `PhotoThumb`、`PhotoStrip`、`PhotoPickerGrid` |
| `ui/plans/PlanCalendarView.kt` | 448 | `PlanCalendarView`、`DayDetailHeader`、`PlanDetailRow`、`EmptyHint`、`cnDate`、`relBadge` |
| `ui/plans/PlanDetailDialog.kt` | 446 | `PlanDetailDialog`、`normalizePlanDate`、`displayPlanDate`、`PlanDueDialog` |
| `ui/plans/PlansScreen.kt` | 991 | `PlansScreen`、`PlansGuideCard`、`PlanRow`、`SortArrow`、`PlanBadge`、`RowIconAction`、`daysBetween`、`RoundIconButton` … |
| `ui/records/MountainBookView.kt` | 843 | `ridgeOf`、`MountainBookView`、`MtnCard`、`MtnDrawer`、`DrawerPill`、`MtnMergeDialog`、`StatCell`、`diffNameOnly` … |
| `ui/records/RecordDetailDialog.kt` | 371 | `RecordDetailDialog`、`Pill`、`fmt1`、`fmtDur` |
| `ui/records/RecordEditDialog.kt` | 514 | `diffLabel`、`normalizeDateInput`、`RecordEditDialog`、`displayDateTime`、`exifDateTime` |
| `ui/records/RecordListScreen.kt` | 931 | `RecordListScreen`、`RecordRow`、`RoundIconButton`、`RecordsGuideCard`、`SmallPill`、`PagerButton`、`SortHeader` |
| `ui/settings/AboutDialogs.kt` | 927 | `Clause`、`LegalClauseCard`、`DocDialog`、`PrivacyPolicyDialog`、`DisclaimerDialog`、`ChangelogDialog`、`VersionBlock`、`parseChangelogBody` … |
| `ui/settings/CloudBackupsDialog.kt` | 285 | `CloudBackupsDialog` |
| `ui/settings/FullChangelog.kt` | 634 |  |
| `ui/settings/LocalBackupsDialog.kt` | 320 | `LocalBackupsDialog`、`agoText` |
| `ui/settings/PhotoUsageDialog.kt` | 314 | `PhotoUsageDialog`、`sizeParts` |
| `ui/settings/SettingsComponents.kt` | 412 | `indigoColor`、`SettingsGroup`、`SettingsItem`、`GlassSwitch`、`GridGlassButton` |
| `ui/settings/SettingsScreen.kt` | 1346 | `SettingsScreen` |
| `ui/settings/SettingsSections.kt` | 64 | `AppearanceSection` |
| `ui/settings/SettingsWidgets.kt` | 491 | `openUrl`、`AboutLink`、`ExportOpt`、`ioScope`、`InfoTile`、`StatInline`、`SettingRow`、`androidxClip` … |
| `ui/settings/SyncConfigDialog.kt` | 193 | `SyncConfigDialog`、`ReadOnlyRow` |
| `ui/share/ShareCardDialog.kt` | 547 | `ShareCardDialog`、`CardMetric`、`renderCardToFile`、`renderCardBitmap`、`probeFont`、`drawShareRow`、`moodText`、`weatherText` … |
| `ui/theme/Design.kt` | 130 |  |
| `ui/theme/Theme.kt` | 250 | `AppTextScale`、`HikingTheme` |

**合计 65 个文件、24436 行**
