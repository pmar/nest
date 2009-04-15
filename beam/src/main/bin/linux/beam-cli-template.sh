#! /bin/sh

export BEAM4_HOME=${installer:sys.installationDir}

if [ -z "$BEAM4_HOME" ]; then
    echo
    echo Error: BEAM4_HOME not found in your environment.
    echo Please set the BEAM4_HOME variable in your environment to match the
    echo location of the BEAM 4.x installation
    echo
    exit 2
fi

"$BEAM4_HOME/jre/bin/java" \
    -Xmx1024M \
    -Dceres.context=beam \
    "-Dbeam.mainClass=${beam.mainClass}" \
    "-Dbeam.processorClass=${beam.processorClass}" \
    "-Dbeam.home=$BEAM4_HOME" \
    "-Dncsa.hdf.hdflib.HDFLibrary.hdflib=$BEAM4_HOME/modules/lib-hdf-2.3/lib/linux/libjhdf.so" \
    "-Dncsa.hdf.hdf5lib.H5.hdf5lib=$BEAM4_HOME/modules/lib-hdf-2.3/lib/linux/libjhdf5.so" \
    -jar "$BEAM4_HOME/bin/ceres-launcher.jar" "$@"

exit 0