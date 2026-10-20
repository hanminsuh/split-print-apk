# 모바일 사진 분할 인쇄 (Android APK & Web 프로젝트)

이 압축 파일에는 안드로이드 스튜디오 설치 없이 GitHub Actions로 `.apk` 설치 파일을 자동 빌드할 수 있는 전체 소스 코드와, 모바일 브라우저에서 단독 실행 가능한 `index.html` 파일이 포함되어 있습니다.

---

## 방법 1. GitHub에 올려서 `.apk` 설치 파일 받기 (PC에 개발 도구 설치 불필요)

1. 이 압축 파일(`split-print-apk.zip`)의 압축을 풉니다.
2. [GitHub](https://github.com)에 로그인한 뒤 새 저장소(New Repository)를 하나 만듭니다.
3. 저장소 화면에서 **uploading an existing file** 링크를 누르고, 압축을 푼 폴더 안의 모든 파일과 폴더(`.github`, `app`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`)를 드래그하여 업로드한 뒤 **Commit changes**를 누릅니다.
   - 주의: `.github` 폴더가 빠지면 자동 빌드가 실행되지 않으므로 숨김 폴더 표시가 꺼져 있다면 확인해 주세요.
4. 상단 메뉴의 **Actions** 탭으로 이동하면 자동으로 `Build Split Print APK` 워크플로우가 실행됩니다 (약 2분 소요).
5. 초록색 체크 표시가 뜨면 해당 워크플로우를 클릭하고, 화면 맨 아래 **Artifacts** 섹션에 있는 **`split-print-apk`**를 눌러 다운로드합니다.
6. 다운로드한 압축 파일 안의 `app-debug.apk`를 스마트폰으로 옮겨 설치합니다.

---

## 방법 2. 빌드 없이 `index.html` 파일만 스마트폰으로 옮겨서 바로 쓰기

1. 압축을 푼 폴더에서 `app/src/main/assets/index.html` 파일 하나만 카카오톡이나 드라이브로 스마트폰에 전송합니다.
2. 스마트폰의 파일 관리자(내 파일)에서 `index.html`을 선택하고 **Chrome** 브라우저로 열면 즉시 사용할 수 있습니다.
