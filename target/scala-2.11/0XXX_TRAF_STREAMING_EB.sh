#!/bin/bash
#/***************************************
# *PROCESO: 0XXX_TRAF_STREAMING_EB
# *DESCRIPCION: Consulta de trafico en Streaming para cruce con Estaciones base
# *Fecha creacion: 2021-05-04
# *Autor: SQDM - Jose Balcucho
# *Número del EPICA 0XXXXX
# ***************************************/
#****************************
#Inicio
#****************************
clear
clear
clear
clear
START=$(date +%s)
SCRIPT=$(readlink -f $0);
path_proceso=`dirname $SCRIPT`;
ext="txt"

ESTA_EJECUTANDO=$(echo "$(cut -d ' ' -f1 <<<$(echo "$(yarn application -list | grep 0XXX_TRAF_STREAMING_EB.sh | awk '{print $2 " " $6}')"))")

if [ $( ps -aux | grep "0XXX_TRAF_STREAMING_EB.sh" | grep -v grep | wc -l | awk '{print "echo `expr " $1 " + 0`" }'  | sh) -gt 2 ] || [ ${#ESTA_EJECUTANDO} -ge 1 ]
then
    echo "Proceso esta actualmente corriendo." && exit
fi



#****************************
#Configuración Aplicacion
#****************************
job_name="JP_0XXX_TRAF_STREAMING_EB"
app_name="0XXX_TRAF_STREAMING_EB"
main_class="com.claro.app"
jar_name="0XXX_TRAF_STREAMING_EB-assembly-0.1.jar"
queue="OTROS"
#queue="PROCESOS_DIARIOS"
path_bats=$path_proceso
path_fuentes="/DWH/DESARROLLO_DWH/12_TRAFICO/0XXX_TRAF_STREAMING_EB/03_FUENTES"
path_log=${path_fuentes}/${app_name}_`date +\%Y\%m\%d\%H\%M`.log

#****************************
#Configuración Recursos
#****************************
executors="8"
cores="6"
executorMemory="8g"
driverMemory="8g"
#****************************
#Configuración Sisnot
#****************************
sisnot_repositorio="WRDEV_DESARROLLO_PROCALIDAD"
#sisnot_repositorio="WRPROD_PRODUCTIVO"
ambiente_sisnot_variables="qa"
#ambiente_sisnot_variables="pr"
ambiente_sisnot_notificaciones="dev"
#ambiente_sisnot_notificaciones="pr"
path_extrae_pr="/DWH/99_ADMINISTRACION/0091_ADMON_SISNOT/02_BATS/0091_extrae_parametros_prm_comcel.jar"
path_ejecuta_escenario="/DWH/99_ADMINISTRACION/0091_ADMON_SISNOT/02_BATS/0091_ejecuta_escenario_sisnot.jar"
topic_voz="0566_claro_voz"
topic_datos="0566_claro_datos"
group_id="TRAF_STREAMING_EB"
brokers_server="tfm1912-hdpcedge01.claro.co:6667,tfm1923-hdpcedge02.local:6667,tfm2043-hdpcedge03.local:6667"
streaming_context_time=10
minutos_consulta=30
echo $hora "Ruta proceso: "${path_proceso}
#****************************
#Ejecución del proceso de Streaming
#****************************
spark-submit --queue ${queue} --master yarn --class ${main_class} --num-executors ${executors} --executor-cores ${cores} --executor-memory ${executorMemory} --driver-memory ${driverMemory} --conf spark.executor.memoryOverhead=24336 --conf spark.driver.maxResultSize=4g --conf spark.default.parallelism=3000 --conf spark.port.maxRetries=100 --conf spark.sql.shuffle.partitions=3000 --conf spark.sql.hive.hiveserver2.jdbc.url="jdbc:hive2://tfm2044-hdpcmtr03.claro.co:2181,tfm2403-hdpcmtr04.claro.co:2181,tfm2404-hdpcmtr05.claro.co:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2" --conf spark.datasource.hive.warehouse.metastoreUri=thrift://tfm2403-hdpcmtr04.claro.co:9083,thrift://tfm2044-hdpcmtr03.claro.co:9083 --conf spark.hadoop.hive.llap.daemon.service.hosts=@llap0 --conf spark.hadoop.hive.zookeeper.quorum=tfm2044-hdpcmtr03.claro.co:2181,tfm2403-hdpcmtr04.claro.co:2181,tfm2404-hdpcmtr05.claro.co:2181 --conf spark.datasource.hive.warehouse.load.staging.dir=/tmp --conf spark.hadoop.metastore.catalog.default=hive --conf spark.serializer=org.apache.spark.serializer.KryoSerializer --conf spark.ui.port=4049 ${path_proceso}/${jar_name} --app_name ${app_name} --job_name ${job_name} --path_extrae_pr ${path_extrae_pr} --path_ejecuta_escenario ${path_ejecuta_escenario} --sisnot_repositorio ${sisnot_repositorio} --ambiente_sisnot_variables ${ambiente_sisnot_variables} --ambiente_sisnot_notificaciones ${ambiente_sisnot_notificaciones} --path_fuentes ${path_fuentes} --topic_voz ${topic_voz} --topic_datos ${topic_datos} --group_id ${group_id} --brokers_server ${brokers_server} --streaming_context_time ${streaming_context_time} --minutos_consulta ${minutos_consulta}
#>> ${path_log}

END=$(date +%s)
DIFF=$(( $END - $START ))
echo "Proceso ejecutado en " ${DIFF}

#****************************
#Fin
#****************************