@REM /*
@REM  * (C) Copyright IBM Corp. 2001, 2020
@REM  *
@REM  * SPDX-License-Identifier: Apache-2.0
@REM  */

@echo OFF
if NOT defined ECHO_FLAG set ECHO_FLAG=OFF
@echo %ECHO_FLAG%

SETLOCAL enabledelayedexpansion
set BATCH_NAME=whBldToolsInstallEng.cmd
set BATCH_VER=2016.11.28.0
set BATCH_OWNER=SHachem
set BATCH_ERR=false


:check
	if "%PREREQ_ROOT%" == "" @echo [ERROR] PREREQ_ROOT is not defined : make sure that 'whBldToolsInstall.cmd' script is used.& goto end

:installPrereq
	if "%1" == "" @echo [ERROR] prereq installation aborted; expected 2 arguments but found nothing. & goto end
	if "%2" == "" @echo [ERROR] prereq installation aborted; expected 2 arguments but found only '%*' & goto end

	set install_root=%PREREQ_ROOT%\%1\%2
	set install_script=%install_root%\install.cmd

	if NOT exist %install_script% @echo [ERROR] no '%install_script%' install script for %1, version %2 & goto end
	if exist %install_script% (
		if "%DEBUG%" == "true" echo [DEBUG] call %install_script% %1 %2 %PLAT% %ARCH% %install_root% %ENGINE_ROOT% %PREREQ_ROOT%
		call %install_script% %1 %2 %PLAT% %ARCH% %install_root% %ENGINE_ROOT% %PREREQ_ROOT%
	)
:aftinstallPrereq

:done
:end
ENDLOCAL
