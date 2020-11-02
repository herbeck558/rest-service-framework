#!/bin/bash -u
#  ************************************** #
#  (C) Copyright IBM Corp. 2001, 2020     #
#  SPDX-License-Identifier: Apache-2.0    #
#  ************************************** #

#:Functions
BATCH_NAME=whBldToolsInstall.bash
BATCH_VER=2017.06.19.0
BATCH_OWNER=SHachem
BATCH_ERR=false

function UsageHelp() {
	echo "${BATCH_NAME} v${BATCH_VER}:"
	echo "Batch script to download required build tools."
	echo "Usage: ${BATCH_NAME} {option}"
	echo "Where {option} is one of the following:"
	echo " -h             : Displays this usage screen"
	echo ""
	echo "Examples:"
	echo "1. ${BATCH_NAME}"
	echo "2. ${BATCH_NAME} ${HOME}/myWorkspace01/bldTools"
	echo "3. export PREREQ_ROOT=/watson/images/whBldTools"
	echo "   ${BATCH_NAME}"
	echo ""
}

#:check
	inArg1=NoInArg
	if [ $# -gt 0 ]; then
		inArg1=$1
	fi
	if [ "${inArg1}" == "-h" ] || [ "${inArg1}" == "-H" ] || [ "${inArg1}" == "-help" ] || [ "${inArg1}" == "--help" ]; then
		UsageHelp
		exit 1
	fi

#:init
	export PLAT=$(uname -s | tr [:upper:] [:lower:])
	export ARCH=$(uname -m)
	lv_tmppwddir=`pwd`
	lv_tmpshdir=`dirname $0`
	cd ${lv_tmpshdir}
		lv_scrdir=`pwd`
	cd ${lv_tmppwddir}
	export DEBUG=${DEBUG:-false}
	export PREREQ_ROOT=${PREREQ_ROOT:-/watson/images/whBldTools}
	if [ "${inArg1}" == "NoInArg" ]; then
		tmpENGINE_ROOT=${lv_scrdir}/../bldTools
	else
		tmpENGINE_ROOT=${inArg1}
	fi
	lv_tmp=${tmpENGINE_ROOT}/tmp
	rm -fr ${lv_tmp}
	mkdir -p ${lv_tmp}
	cd ${tmpENGINE_ROOT}
		export ENGINE_ROOT=`pwd`
	cd ${lv_tmppwddir}

#:main
	grep -v "^#" ${lv_scrdir}/whBldToolsInstall.properties > ${lv_tmp}/whBldToolsInstall.proptmp
	rm -f ${lv_tmp}/env.whBldToolsInstall.bash
	command echo "#!/bin/bash -u" >> ${lv_tmp}/env.whBldToolsInstall.bash
	command echo ". ${lv_scrdir}/whBldToolsInstallEng.bash" >> ${lv_tmp}/env.whBldToolsInstall.bash
	command echo "" >> ${lv_tmp}/env.whBldToolsInstall.bash
	while read aLine; do
		if [ -n "${aLine}" ]; then
			command echo "installPrereq ${aLine}" >> ${lv_tmp}/env.whBldToolsInstall.bash
		fi
	done < ${lv_tmp}/whBldToolsInstall.proptmp

	echo "`date` [INFO] Downloading build tools from '${PREREQ_ROOT}' to '${ENGINE_ROOT}' ..."
	chmod 700 ${lv_tmp}/env.whBldToolsInstall.bash
	bash ${lv_tmp}/env.whBldToolsInstall.bash
	echo "`date` [INFO] Download is completed."

#:done
	rm -fr ${lv_tmp}

#:end
