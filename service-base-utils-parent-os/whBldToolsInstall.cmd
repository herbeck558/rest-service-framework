@REM /*
@REM  * (C) Copyright IBM Corp. 2001, 2020
@REM  *
@REM  * SPDX-License-Identifier: Apache-2.0
@REM  */

@echo OFF
if NOT defined ECHO_FLAG set ECHO_FLAG=OFF
@echo %ECHO_FLAG%

SETLOCAL enabledelayedexpansion
set BATCH_NAME=whBldToolsInstall.cmd
set BATCH_VER=2017.06.19.0
set BATCH_OWNER=SHachem
set BATCH_ERR=false

if NOT "%1" == "-h" if NOT "%1" == "-H" goto aftusage
:usage
	@echo %BATCH_NAME% v%BATCH_VER%:
	@echo Batch script to download required build tools.
	@echo Usage: %BATCH_NAME% {option}
	@echo Where {option} is one of the following:
	@echo  -h             : Displays this usage screen
	@echo.
	@echo Examples:
	@echo 1. %BATCH_NAME%
	@echo 2. %BATCH_NAME% D:\wh\myWorkspace01\bldTools
	@echo 3. set PREREQ_ROOT=z:\images\whBldTools
	@echo    %BATCH_NAME%
	@echo.
	goto end
:aftusage

:check
	if /I "%1" EQU "--help" goto usage

:init
	set PLAT=windows
	set ARCH=x86_64
	set lv_scrdir=%~dp0
	pushd %lv_scrdir%
		set lv_scrdir=%CD%
	popd
	if "%DEBUG%" == "" set DEBUG=false
	if "%PREREQ_ROOT%" == "" set PREREQ_ROOT=w:\images\whBldTools
	if NOT "%1" == "" set tmpENGINE_ROOT=%1
	if "%tmpENGINE_ROOT%" == "" set tmpENGINE_ROOT=%lv_scrdir%\..\bldTools
	set lv_tmp=%tmpENGINE_ROOT%\tmp
	if exist %lv_tmp% rd /s/q %lv_tmp%
	md %lv_tmp%
	pushd %tmpENGINE_ROOT%
		set ENGINE_ROOT=%CD%
	popd

:main
	findstr /v /r /c:"^#" %lv_scrdir%\whBldToolsInstall.properties > %lv_tmp%\whBldToolsInstall.proptmp
	if exist %lv_tmp%\env.whBldToolsInstall.cmd del %lv_tmp%\env.whBldToolsInstall.cmd
	for /F "tokens=*" %%I in (%lv_tmp%\whBldToolsInstall.proptmp) do (
		if NOT "%%I" == "" @echo call %lv_scrdir%\whBldToolsInstallEng.cmd %%I>> %lv_tmp%\env.whBldToolsInstall.cmd
	)

	@echo %DATE% %TIME% [INFO] Downloading build tools from '%PREREQ_ROOT%' to '%ENGINE_ROOT%' ...
	call %lv_tmp%\env.whBldToolsInstall.cmd
	@echo %DATE% %TIME% [INFO] Download is completed.
:aftmain

:done
	rd /s/q %lv_tmp%

:end
ENDLOCAL
