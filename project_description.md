# 📱 Project: Org-Roam Mobile (MVP v0.1)

## 🎯 Goal

A minimal, modern Android app that lets you **view, edit, and navigate
org-roam notes** stored locally on your device.\
You point it at your synced folder (Nextcloud, Syncthing, Git, etc.),
and it provides:

-   A list of all *.org* files (titles extracted from *#+title:*)
-   A simple note viewer + editor
-   Link following (*\[\[file:\...\]\]*)
-   Backlink listing via lightweight SQLite index
-   Continuous re-index when files change
-   Ready for extension (WebDAV, Git sync, graph view)

## 🧱 Architecture Overview

*App Layer (Jetpack Compose UI)*

* ├── NoteListScreen*

* ├── NoteViewScreen*

* ├── NoteEditScreen*

* └── SettingsScreen*

* ↳ Folder picker for local sync*

*Domain Layer (MVVM ViewModels)*

* ├── NoteRepository*

* ├── ParserService*

* ├── IndexService*

* ├── SyncManager (pluggable)*

* └── EventBus (Flow-based)*

*Data Layer*

* ├── FileStorage (Local)*

* ├── Parser (Regex-based)*

* ├── SQLite Index (Room)*

* └── Sync (LocalFolderSync)*

**Principles:**

-   Offline-first (local files are source of truth)
-   Clean separation via interfaces
-   Continuous verification (unit tests + CI)
-   Each subsystem can be replaced later (DI)

## ⚙️ Tech Stack

  -------------- --------------------------------------------
  Language       Kotlin
  UI             Jetpack Compose
  Architecture   MVVM + Hilt/Koin for DI
  Database       Room (SQLite)
  Reactive       Kotlin Coroutines + Flow
  Testing        JUnit + MockK
  CI/CD          Gradle + GitHub Actions
  Sync           Local Folder (SAF) --- MVP; WebDAV planned
  Min SDK        26+ (Android 8.0+)
  -------------- --------------------------------------------

## 🗂️ Directory Layout

*orgroam-mobile/*

* ├── app/*

* │ ├── data/*

* │ │ ├── model/Note.kt*

* │ │ ├── storage/IStorage.kt*

* │ │ ├── storage/FileStorageImpl.kt*

* │ │ ├── parser/IParser.kt*

* │ │ ├── parser/RegexParser.kt*

* │ │ ├── index/NoteDatabase.kt*

* │ │ ├── index/NoteDao.kt*

* │ │ ├── sync/SyncProvider.kt*

* │ │ ├── sync/LocalFolderSync.kt*

* │ │ └── sync/SyncProviderFactory.kt*

* │ ├── domain/*

* │ │ ├── repository/NoteRepository.kt*

* │ │ ├── service/ParserService.kt*

* │ │ ├── service/IndexService.kt*

* │ │ └── service/SyncManager.kt*

* │ ├── ui/*

* │ │ ├── NoteListScreen.kt*

* │ │ ├── NoteViewScreen.kt*

* │ │ ├── NoteEditScreen.kt*

* │ │ └── SettingsScreen.kt*

* │ └── viewmodel/*

* │ ├── NoteListViewModel.kt*

* │ ├── NoteViewViewModel.kt*

* │ ├── NoteEditViewModel.kt*

* │ └── SettingsViewModel.kt*

* └── tests/*

* ├── ParserTests.kt*

* ├── IndexTests.kt*

* └── StorageTests.kt*

## 🧩 Core Interfaces (MVP skeleton)

### 1️⃣ Data Models

*data class NoteId(val path: String)*

*data class NoteLink(val from: String, val to: String, val label:
String?)*

*data class Note(*

* val id: NoteId,*

* val title: String,*

* val content: String,*

* val links: List\<NoteLink\>*

*)*

### 2️⃣ Storage Layer

*interface IStorage {*

* suspend fun listNotes(): List\<File\>*

* suspend fun readNote(path: String): String*

* suspend fun writeNote(path: String, content: String)*

*}*

*class FileStorageImpl(private val baseDir: File) : IStorage {*

* override suspend fun listNotes() = baseDir.walk()*

* .filter { it.extension == \"org\" }*

* .toList()*

* override suspend fun readNote(path: String) = File(baseDir,
path).readText()*

* override suspend fun writeNote(path: String, content: String) =*

* File(baseDir, path).writeText(content)*

*}*

### 3️⃣ Parser Layer (MVP Regex)

*interface IParser {*

* fun parse(content: String, path: String): Note*

*}*

*class RegexParser : IParser {*

* private val titleRegex = Regex(\"\"\"#\\+title:\\s\*(.\*)\"\"\",
RegexOption.IGNORE_CASE)*

* private val linkRegex =
Regex(\"\"\"\\\[\\\[file:(.\*?)\\\]\\\[?(.\*?)\\\]?\\\]\"\"\")*

* override fun parse(content: String, path: String): Note {*

* val title = titleRegex.find(content)?.groupValues?.get(1) ?: path*

* val links = linkRegex.findAll(content).map {*

* NoteLink(path, it.groupValues\[1\], it.groupValues.getOrNull(2))*

* }.toList()*

* return Note(NoteId(path), title, content, links)*

* }*

*}*

### 4️⃣ Index Layer (Room)

*\@Entity(tableName = \"notes\")*

*data class NoteEntity(@PrimaryKey val path: String, val title: String)*

*\@Entity(tableName = \"links\")*

*data class LinkEntity(val source: String, val target: String)*

*\@Dao*

*interface NoteDao {*

* \@Query(\"SELECT \* FROM notes\") fun getAll(): List\<NoteEntity\>*

* \@Insert(onConflict = OnConflictStrategy.REPLACE) fun insertAll(notes:
List\<NoteEntity\>)*

* \@Query(\"DELETE FROM notes\") fun clearNotes()*

* \@Query(\"SELECT source FROM links WHERE target = :path\") fun
getBacklinks(path: String): List\<String\>*

*}*

*\@Database(entities = \[NoteEntity::class, LinkEntity::class\], version
= 1)*

*abstract class NoteDatabase : RoomDatabase() {*

* abstract fun noteDao(): NoteDao*

*}*

### 5️⃣ Repository

*class NoteRepository(*

* private val storage: IStorage,*

* private val parser: IParser,*

* private val dao: NoteDao*

*) {*

* suspend fun reindex() {*

* val files = storage.listNotes()*

* val notes = files.map {
parser.parse(storage.readNote(it.relativeTo(storageBase).path), it.name)
}*

* dao.clearNotes()*

* dao.insertAll(notes.map { NoteEntity(it.id.path, it.title) })*

* }*

* suspend fun getBacklinks(path: String): List\<String\> =
dao.getBacklinks(path)*

*}*

### 6️⃣ Sync Layer

#### Interface

*interface SyncProvider {*

* suspend fun pull(): Result\<Unit\>*

* suspend fun push(): Result\<Unit\>*

* fun isOnline(): Boolean*

*}*

#### MVP Implementation

*class LocalFolderSync(private val baseDir: File) : SyncProvider {*

* override suspend fun pull() = Result.success(Unit) // no-op*

* override suspend fun push() = Result.success(Unit) // no-op*

* override fun isOnline() = true*

*}*

### 7️⃣ ViewModels

Each *ViewModel* uses a repository instance. Example:

*class NoteListViewModel(private val repo: NoteRepository) : ViewModel()
{*

* private val \_notes = MutableStateFlow\<List\<Note\>\>(emptyList())*

* val notes: StateFlow\<List\<Note\>\> = \_notes*

* fun loadNotes() = viewModelScope.launch {*

* repo.reindex()*

* \_notes.value = repo.getAllNotes()*

* }*

*}*

### 8️⃣ UI (Compose)

**NoteListScreen.kt**

*\@Composable*

*fun NoteListScreen(viewModel: NoteListViewModel, onOpenNote: (String)
-\> Unit) {*

* val notes by viewModel.notes.collectAsState()*

* LazyColumn {*

* items(notes) { note -\>*

* Text(*

* text = note.title,*

* modifier = Modifier*

* .fillMaxWidth()*

* .clickable { onOpenNote(note.id.path) }*

* .padding(16.dp)*

* )*

* }*

* }*

*}*

### 9️⃣ CI/CD

**.github/workflows/android.yml**

*name: Android CI*

*on: \[push, pull_request\]*

*jobs:*

* build:*

* runs-on: ubuntu-latest*

* steps:*

*  - uses: actions/checkout@v4*

*  - name: Setup JDK*

* uses: actions/setup-java@v3*

* with:*

* java-version: \'17\'*

*  - name: Build and test*

* run: ./gradlew build test*

### 🔄 Incremental Development Roadmap

  ---- -------------------------- ----------------------
  1️⃣   File read + note list      Verify listing
  2️⃣   View note                  Check link parsing
  3️⃣   Backlink index             Run integration test
  4️⃣   Editor + save              Roundtrip
  5️⃣   Reactive updates           Flow tests
  6️⃣   CI pipeline                Green builds
  7️⃣   Settings + folder picker   Manual test
  ---- -------------------------- ----------------------

### 🧠 Post-MVP Extensions

-   WebDAV sync via *sardine-android*
-   Git sync via JGit
-   Graph view (Compose Canvas)
-   Org-parser plugin (full syntax)
-   AI summarization / embeddings
-   Cross-platform (Kotlin Multiplatform Desktop/iOS)

## ✅ Next Step

You can now:

1.  Create a new **Android Studio project** with this layout.
2.  Copy the above Kotlin interfaces and Compose skeletons.
3.  Implement iteration 1 (FileStorage + NoteListScreen).
4.  Let your coding agent scaffold the boilerplate (*gradle*, DI, Room
    setup).
5.  Commit after each working step (continuous verification).
