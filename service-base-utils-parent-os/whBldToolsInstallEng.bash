#!/bin/bash -u
#  ************************************** #
#  (C) Copyright IBM Corp. 2001, 2020     #
#  SPDX-License-Identifier: Apache-2.0    #
#  ************************************** #

#:Functions
BATCH_NAME=whBldToolsInstallEng.bash
BATCH_VER=2016.11.28.0
BATCH_OWNER=SHachem
BATCH_ERR=false

# display an error message to STDERR
# see above comment for the "log" function on why this function should be
# preferred to using naked echo statments
function error() {
	echo "[ERROR] ${*}" 1>&2
}

# display a logging message to STDOUT
# this function should be preferred over naked echo statements as this function
# allows for the possibility of doing something other than (or perhaps in
# addition to) printing to STDOUT in the future
function log() {
	echo "[INFO] ${*}"
}

# display a diagnostic message to STDERR
function debug() {
	isTrue ${DEBUG-false} && echo "[DEBUG] ${*}" 1>&2 || true
}

# check if something (usually an environment variable) is true
# $1 - the thing to check; assume false is unspecified
function isTrue() {
	local truth=$(command echo "${1-false}" | tr [:upper:] [:lower:])
	case ${truth} in
		yes|true)
			return 0
			;;
		*)
			return 1
			;;
	esac
}

function installPrereq() {
	# make sure that we were given the number of arguments we expected
	if [[ ${#} -ne 2 ]]; then
		echo "[ERROR] prereq installation aborted; expected 2 arguments but found ${#}"
		exit 1
	fi

	local install_root=${PREREQ_ROOT}/${1}/${2}
	local install_script=${install_root}/install

	if [[ -f ${install_script} ]]; then
		if [ "${DEBUG}" == "true" ] || [ "${DEBUG}" == "TRUE" ]; then
			echo "[DEBUG] . ${install_script} ${1} ${2} ${PLAT} ${ARCH} ${install_root} ${ENGINE_ROOT}"
		fi
		. ${install_script} ${1} ${2} ${PLAT} ${ARCH} ${install_root} ${ENGINE_ROOT}
	else
		echo "[ERROR] no '${install_script}' install script for ${1}, version ${2}"
		exit 1
	fi
}

#:check
	PREREQ_ROOT=${PREREQ_ROOT:-false}
	if [ "${PREREQ_ROOT}" == "false" ]; then
		echo "[ERROR] PREREQ_ROOT is not defined : make sure that 'whBldToolsInstall.bash' script is used."
		exit 1
	fi

#:done
#:end
