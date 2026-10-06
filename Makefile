GRADLEW := .\gradlew.bat
JAR_DIR := build\libs
SHARED_SOURCE_DIR := C:\hytale-shared-source
SHARED_RUN_DIR := $(SHARED_SOURCE_DIR)\run
SHARED_RUN_SCRIPT := $(SHARED_SOURCE_DIR)\scripts\run.bat

.PHONY: build
build: ## Compile and package the mod
	$(GRADLEW) jar

.PHONY: test
test: ## Run the mod test suite
	$(GRADLEW) test

.PHONY: benchmark
benchmark: ## Run benchmarks after JMH is configured for this mod
	@echo No benchmark is configured. Add JMH before using this target.

.PHONY: copy-mod
copy-mod: ## Copy the current mod jar to the shared-source test runtime
copy-mod: build
	powershell -NoProfile -ExecutionPolicy Bypass -Command "$$jar = Get-ChildItem -Path '$(JAR_DIR)' -Filter '*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if (-not $$jar) { throw 'No jar found. Run make build first.' }; New-Item -ItemType Directory -Force -Path '$(SHARED_RUN_DIR)\mods' | Out-Null; Copy-Item -Path $$jar.FullName -Destination '$(SHARED_RUN_DIR)\mods' -Force"

.PHONY: run-test-server
run-test-server: ## Build, deploy, and start the shared-source test server
run-test-server: copy-mod
	cmd.exe /c "$(SHARED_RUN_SCRIPT)"
