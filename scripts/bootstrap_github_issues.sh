#!/usr/bin/env bash
set -euo pipefail

# Requires: GitHub CLI (gh) authenticated with repo access.

if ! command -v gh >/dev/null 2>&1; then
  echo "Error: gh (GitHub CLI) is not installed." >&2
  exit 1
fi

REPO="${REPO:-}"
GH_REPO_ARGS=()
if [[ -z "$REPO" ]]; then
  # Derive from current git repo if not provided
  if gh repo view --json name,owner >/dev/null 2>&1; then
    REPO=$(gh repo view --json name,owner --jq '.owner.login+"/"+.name')
  else
    echo "Error: REPO not set and unable to infer current GitHub repo." >&2
    exit 1
  fi
fi
GH_REPO_ARGS+=(--repo "$REPO")

create_or_update_label() {
  local name="$1" color="$2" desc="$3"
  if ! gh label create "$name" --color "$color" --description "$desc" "${GH_REPO_ARGS[@]}" 2>/dev/null; then
    gh label edit "$name" --color "$color" --description "$desc" "${GH_REPO_ARGS[@]}" >/dev/null
  fi
}

get_milestone_number() {
  local title="$1"
  gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[] | select(.title=="'"$title"'") | .number' 2>/dev/null | head -n1 || true
}

create_milestone() {
  local name="$1" desc="$2"
  local num
  num=$(get_milestone_number "$name")
  if [[ -z "$num" ]]; then
    gh api -X POST "repos/$REPO/milestones" -f title="$name" -f description="$desc" >/dev/null
  fi
}

issue_exists() {
  local title="$1"
  local found
  found=$(gh issue list "${GH_REPO_ARGS[@]}" --search "$title in:title" --state all --json title --jq '.[] | select(.title=="'"$title"'") | .title' || true)
  [[ "$found" == "$title" ]]
}

create_issue() {
  local title="$1" milestone_title="$2" labels_csv="$3" body="$4"
  if issue_exists "$title"; then
    echo "Skipping existing issue: $title"
    return 0
  fi
  # Ensure milestone exists and get its number
  create_milestone "$milestone_title" ""
  local ms_num
  ms_num=$(get_milestone_number "$milestone_title")
  local args=(issue create --title "$title" --body "$body" --milestone "$milestone_title")
  IFS=',' read -r -a labels <<< "$labels_csv"
  for l in "${labels[@]}"; do
    args+=(--label "$l")
  done
  gh "${args[@]}" "${GH_REPO_ARGS[@]}" >/dev/null
  echo "Created: $title"
}

echo "Creating labels..."
labels=(
  "feat;0e8a16;Feature"
  "fix;d73a4a;Bug fix"
  "refactor;fbca04;Refactor"
  "docs;0075ca;Documentation"
  "test;a2eeef;Tests"
  "chore;cfd3d7;Chore / CI / Tooling"
  "parser;5319e7;Area: parser"
  "storage;5319e7;Area: storage"
  "index;5319e7;Area: index"
  "domain;5319e7;Area: domain"
  "repository;5319e7;Area: repository"
  "service;5319e7;Area: service"
  "ui;5319e7;Area: UI"
  "viewmodel;5319e7;Area: ViewModel"
  "sync;5319e7;Area: sync"
  "ci;5319e7;Area: CI"
  "tooling;5319e7;Area: tooling"
  "settings;5319e7;Area: settings"
  "P0;e11d21;Priority: must-have"
  "P1;fbca04;Priority: should-have"
  "P2;0e8a16;Priority: nice-to-have"
  "backlog;ededed;Status: backlog"
  "in-progress;ededed;Status: in-progress"
  "blocked;ededed;Status: blocked"
)
for entry in "${labels[@]}"; do
  IFS=';' read -r name color desc <<< "$entry"
  create_or_update_label "$name" "$color" "$desc"
done

echo "Creating milestones..."
create_milestone "M0 - Project Setup" "Project scaffold, dependencies, CI"
create_milestone "M1 - File Read + Note List" "List .org notes, titles, basic list UI"
create_milestone "M2 - View Note" "Open note, render content, follow links"
create_milestone "M3 - Backlink Index" "Room DB, reindexing, backlinks"
create_milestone "M4 - Editor + Save" "Edit and save .org content"
create_milestone "M5 - Reactive Updates" "Flows for notes and indexing updates"
create_milestone "M6 - CI Pipeline Hardening" "Stable CI with tests and lint"
create_milestone "M7 - Settings + Folder Picker" "SAF folder picker and DataStore"

echo "Creating issues..."

# M0
create_issue \
  "M0: Project scaffold and CI" \
  "M0 - Project Setup" \
  "backlog,chore,ci,tooling,P0" \
  "$(cat <<'EOF'
Description
Scaffold Android project with Kotlin + Compose, configure dependencies and CI per AGENTS.md and project_description.md.

Tasks
- Initialize Gradle project (Kotlin, Compose, minSdk 26, Java 17)
- Add dependencies: Coroutines/Flow, Room, Hilt or Koin, DataStore, MockK, JUnit
- Decide DI framework (Hilt vs Koin) and scaffold modules
- Create package layout: app/data|domain|ui|viewmodel and tests mirror
- Add .github/workflows/android.yml running build + tests

Acceptance Criteria
- Project builds locally with ./gradlew build
- CI workflow is green on default branch
EOF
)"

# M1
create_issue \
  "M1: File read + note list (MVP)" \
  "M1 - File Read + Note List" \
  "backlog,feat,storage,parser,domain,repository,viewmodel,ui,P0" \
  "$(cat <<'EOF'
Description
Implement models, storage, parser, repository, NoteListViewModel, and NoteListScreen to list .org notes with titles.

Tasks
- Data models: NoteId, NoteLink(label optional), Note
- Storage: IStorage, FileStorageImpl (.org filter, read/write)
- Parser: IParser, RegexParser (#+title:, [[file:...][alias?]])
- Repository: list all notes from filesystem
- ViewModel: NoteListViewModel exposes notes as StateFlow
- UI: NoteListScreen with click to open note
- Tests: StorageTests, ParserTests, NoteListViewModel tests

Acceptance Criteria
- List shows all .org files’ titles (fallback to path)
- Unit tests cover parser edge cases and storage
EOF
)"

# M2
create_issue \
  "M2: View note screen and navigation" \
  "M2 - View Note" \
  "backlog,feat,ui,viewmodel,parser,P0" \
  "$(cat <<'EOF'
Description
Add NoteViewViewModel and NoteViewScreen to display content and clickable links; wire navigation from list.

Tasks
- ViewModel: load note by path; expose content + links
- UI: render content and handle link clicks
- Navigation from list -> view
- Tests: viewmodel behavior and parser link extraction

Acceptance Criteria
- Tapping a list item opens the note view
- Links navigate to target notes when present
EOF
)"

# M3
create_issue \
  "M3: Backlink index (Room)" \
  "M3 - Backlink Index" \
  "backlog,feat,index,domain,repository,P0" \
  "$(cat <<'EOF'
Description
Introduce Room database with NoteEntity, LinkEntity, NoteDao, and reindexing to populate backlinks.

Tasks
- Room: entities, DAO, NoteDatabase (v1)
- Repository: reindex(), getBacklinks(path)
- Integrate reindex on app start and after edits
- UI: show backlinks in note view
- Tests: IndexTests (insert/clear/backlinks), repository integration

Acceptance Criteria
- getBacklinks(path) returns sources referencing target
- Reindex is idempotent and updates on changes
EOF
)"

# M4
create_issue \
  "M4: Editor and save flow" \
  "M4 - Editor + Save" \
  "backlog,feat,ui,viewmodel,storage,P1" \
  "$(cat <<'EOF'
Description
Provide a simple note editor and save functionality using IStorage, updating repository/index afterward.

Tasks
- ViewModel: NoteEditViewModel (load, edit, save)
- UI: NoteEditScreen (basic text area, save)
- Write via IStorage, trigger reindex, navigate back
- Tests: storage roundtrip; edit ViewModel save

Acceptance Criteria
- Editing and saving persists changes and updates list/backlinks
EOF
)"

# M5
create_issue \
  "M5: Reactive updates with Flow" \
  "M5 - Reactive Updates" \
  "backlog,feat,service,viewmodel,P1" \
  "$(cat <<'EOF'
Description
Expose Flow/StateFlow for notes and indexing updates; wire screens to observe changes.

Tasks
- Convert repository/ViewModels to Flow/StateFlow
- Trigger updates after writes/reindex
- Tests: coroutine test utilities for emission order

Acceptance Criteria
- List/view update without manual refresh after edits
EOF
)"

# M6
create_issue \
  "M6: CI pipeline hardening" \
  "M6 - CI Pipeline Hardening" \
  "backlog,chore,ci,P1" \
  "$(cat <<'EOF'
Description
Stabilize CI with reliable unit tests and caching for faster builds.

Tasks
- Ensure tests deterministic on CI
- Add Gradle caches in workflow
- Optional: add lint/detekt steps if configured

Acceptance Criteria
- Consistent green CI on PRs and pushes
EOF
)"

# M7
create_issue \
  "M7: Settings + folder picker (SAF)" \
  "M7 - Settings + Folder Picker" \
  "backlog,feat,settings,storage,sync,viewmodel,ui,P1" \
  "$(cat <<'EOF'
Description
Add Settings screen with SAF folder picker and persist base directory via DataStore; wire into FileStorageImpl.

Tasks
- SettingsViewModel and SettingsScreen
- Persist base dir using DataStore
- Inject base dir into FileStorageImpl
- SyncProvider interface + LocalFolderSync no-ops
- Tests: DataStore read/write; storage path resolution

Acceptance Criteria
- Base directory selectable and remembered across launches
EOF
)"

echo "Done."
