package com.claro

import java.lang
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar

import com.claro.utils.notifications.{error_proceso, fin_proceso, inicio_proceso, log}
import com.claro.utils.file_utils
import com.claro.utils.parameteres.getOptionParameters
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.util.LongAccumulator
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.sys.process._


object app {
  def main(args: Array[String]): Unit = {
    //===============================================
    //Set level log Error
    Logger.getLogger("org").setLevel(Level.ERROR)
    //===============================================
    //Read sh Parameters
    log("Read SH Parameters")
    val parametros = getOptionParameters(mutable.HashMap.empty[Symbol, String], args.toList)
    val app_name: String = parametros.get('app_name).get.toString
    val job_name: String = parametros.get('job_name).get.toString
    val sisnot_repositorio: String = parametros.get('sisnot_repositorio).get.toString
    val ambiente_sisnot_variables: String = parametros.get('ambiente_sisnot_variables).get.toString
    val ambiente_sisnot_notificaciones: String = parametros.get('ambiente_sisnot_notificaciones).get.toString
    val path_fuentes: String = parametros.get('path_fuentes).get.toString
    val path_extrae_pr: String = parametros.get('path_extrae_pr).get.toString
    val path_ejecuta_escenario: String = parametros.get('path_ejecuta_escenario).get.toString
    val repo_sisnot = ambiente_sisnot_notificaciones + " " + sisnot_repositorio
    val topic_voz: String = parametros.get('topic_voz).get.toString
    val topic_datos: String = parametros.get('topic_datos).get.toString
    val group_id: String = parametros.get('group_id).get.toString
    val brokers_server: String = parametros.get('brokers_server).get.toString
    val streaming_context_time: String = parametros.get('streaming_context_time).get.toString
    val minutos_consulta: String = parametros.get('minutos_consulta).get.toString
    val minutos = minutos_consulta.toInt
    val temp_folder: String = "TEMPORAL"
    val ext= "txt"

    //===============================================
    log(app_name + " Start Process Spark")

    val spark_configuration = new SparkConf().setAppName(app_name)
    val ss = SparkSession.builder()
      .config(spark_configuration)
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .config("spark.sql.warehouse.dir", "/warehouse/tablespace/managed/hive")
      .config("hive.metastore.uris", "thrift://tfm2403-hdpcmtr04.claro.co:9083,thrift://tfm2044-hdpcmtr03.claro.co:9083")
      .enableHiveSupport()
      .getOrCreate()

    inicio_proceso(path_ejecuta_escenario, repo_sisnot, job_name)
    fin_proceso(path_ejecuta_escenario, repo_sisnot, job_name)

    try {

      log("Read Sisnot Parameters")
      val variables_sisnot = new sisnot_processor(ss)
      variables_sisnot.getparamsFile(path_extrae_pr, path_fuentes, ambiente_sisnot_variables, job_name, ext, temp_folder)
      variables_sisnot.getParamsDF(path_fuentes + "/" + temp_folder + "/" + job_name + "." + ext)


      val path_preprocesados = variables_sisnot.getParamValue(job_name, "V_1016_PATCH_PREPROCESADOS")
      val path_procesados = variables_sisnot.getParamValue(job_name, "V_1016_PATCH_PROCESADOS")
      val path_preprocesados_tmp= variables_sisnot.getParamValue(job_name, "V_1016_PATCH_PREPROCESADOS_TMP")
      val Nombre_archivo= variables_sisnot.getParamValue(job_name, "V_1016_FILE_NAME")
      val topico = variables_sisnot.getParamValue(job_name, "V_1016_FILE_NAME")


      //===============================================
      //Tabla de estaciones b
      val Estacion_base_df = ss.sql("select nombre, latitud_eb, longitud_eb, eb from desarrollo.tbl_tmp_estaciones_base where nombre like '%TJV%'")
      val total_EB = Estacion_base_df.count()


      if(total_EB==0){
        throw new Exception("No hay registos de estaciones base")
      }


      log("total estaciones base: " + total_EB)
      Estacion_base_df.createOrReplaceTempView("tiendas")
      //===============================================
      val celdas = ss.sql("select trim(celda) celda, trim(lac) lac, longitud , latitud, descripcion_bts from desarrollo.data_celdas_streaming")
      val total_celdas = celdas.count()
      log("total celdas: " + total_celdas)
      celdas.createOrReplaceTempView("celdas")
      //===============================================
      //Celdas Datos
      val celdas_datos = ss.sql("select trim(celda) celda, trim(lac) lac, longitud , latitud, descripcion_bts from celdas where descripcion_bts LIKE '%_T1%'")
      val total_celdas_datos = celdas_datos.count()
      log("total celdas datos: " + total_celdas_datos)
      celdas_datos.createOrReplaceTempView("celdas_datos")
      //===============================================
      //Tabla de Estaciones Locales
      val tiendas_antenas_voz = ss.sql("select * from tiendas t left join celdas a on t.latitud_eb= a.latitud and t.longitud_eb= a.longitud")
      val tiendas_antenas_datos = ss.sql("select * from tiendas t left join celdas_datos a on t.latitud_eb= a.latitud and t.longitud_eb= a.longitud")


      //Tabla de Clientes
      val clientes = ss.sql("select '57'||tele_numb as tele_numb, estado, tipo_linea from clientes.inh_seg_bscs_clientes where estado = 'a'")
      clientes.createOrReplaceTempView("tabla_clientes")


      tiendas_antenas_voz.createOrReplaceTempView("tiendas_voz")
      tiendas_antenas_datos.createOrReplaceTempView("tiendas_datos")

      val total_tiendas_voz = tiendas_antenas_voz.count()
      val total_tiendas_datos = tiendas_antenas_datos.count()
      log("total tiendas voz: " + total_tiendas_voz)
      log("total tiendas datos: " + total_tiendas_datos)


      //===============================================
      val topics = Array(topico)
      val batchInterval = Seconds(30)
      val streamingContext = new StreamingContext(ss.sparkContext, batchInterval)


      val kafkaParams = Map[String, Object](
        "bootstrap.servers" -> brokers_server,
        "key.deserializer" -> classOf[StringDeserializer],
        "value.deserializer" -> classOf[StringDeserializer],
        "group.id" -> group_id,
        "auto.offset.reset" -> "latest",
        "enable.auto.commit" -> (true: java.lang.Boolean)
      )

      val stream = KafkaUtils.createDirectStream[String, String](
        streamingContext,
        LocationStrategies.PreferBrokers,
        Subscribe[String, String](topics, kafkaParams)
      )

      val trafico = stream.map(record => record.value)

      trafico.foreachRDD { rdd =>
        import ss.implicits._
        val df = new SimpleDateFormat("yyyyMMddHHmmss")
        val calendar = Calendar.getInstance()
        val fecha_reporte = df.format(calendar.getTime)
        calendar.add(Calendar.MINUTE, -60);
        val time_stream = df.format(calendar.getTime).toDouble
        print(".")
        val df_trafico = rdd.toDF("msg")
        if (df_trafico.count() > 0) {
          log("Total trafico: " + df_trafico.count())

          //===============================================
          // DATOS
          //===============================================
          log("---DATOS-------------------------------------------------")
          Try {
            val r = Seq("hdfs", "dfs", "-rm", path_preprocesados_tmp).!!
          }
          val df_datos = df_trafico.withColumn("valor", regexp_replace($"msg", "'", ""))
            .filter(col("valor").contains("DATOS"))
            .withColumn("tele_numb", split(col("valor"), ",").getItem(1))
            .withColumn("imsi", split(col("valor"), ",").getItem(2))
            .withColumn("imei", split(col("valor"), ",").getItem(3))
            .withColumn("record_opening_time", split(col("valor"), ",").getItem(4))
            .withColumn("time_stream", split(col("valor"), ",").getItem(4).cast("Double"))
            .withColumn("cellId", split(col("valor"), ",").getItem(5))
            .withColumn("idLac", split(col("valor"), ",").getItem(6))

          log("total trafico datos entrante: " + df_datos.count())
          //df_datos.show(10)

          val trafico_datos_tmp = df_datos.filter(col("time_stream").>(time_stream)).createOrReplaceTempView("trafico_datos_tmp")

          val trafico_datos = ss.sql("with trafico_d as(select tele_numb , trim(cellId) cellId  , trim(idLac) idLac, record_opening_time, " +
            "row_number() over (partition by tele_numb, cellId, idLac  order by record_opening_time desc) as order_trafico " +
            "FROM  trafico_datos_tmp ) select tele_numb, cellId , idLac, record_opening_time from trafico_d  where order_trafico =1 and tele_numb like '57%'")
          trafico_datos.createOrReplaceTempView("trafico_datos")

          val total_traf_datos_reciente = trafico_datos.count()
          log("total trafico reciente datos: " + total_traf_datos_reciente)
          if (total_traf_datos_reciente > 0) {
            trafico_datos.show(10)
            val trafico_cercano_datos = ss.sql("select tc.tipo_linea ,tr.tele_numb from  trafico_datos tr inner join tiendas_datos td " +
              "on td.celda= tr.cellId and td.lac = tr.idLac inner join tabla_clientes tc on tr.tele_numb = tc.tele_numb")
            trafico_cercano_datos.createOrReplaceTempView("trafico_general_datos")
            val Num_TCD = trafico_cercano_datos.count()
            log("total tráfico cercano: " + Num_TCD)


            //Tráfico cercano datos prepago
            val trafico_segmento_datos_prep = ss.sql("select tgd.tele_numb || '|jvaldez|' from trafico_general_datos tgd where tipo_linea = 'Prepago'")
            val Num_TCDPre = trafico_segmento_datos_prep.count()
            log("total tráfico cercano prepago: " + Num_TCDPre)
            trafico_segmento_datos_prep.show(5)

            if (Num_TCDPre > 0) {
              generateFile(trafico_cercano_datos, path_preprocesados + "/TMP" , Nombre_archivo + fecha_reporte + "_PREP"  , ss, ss.sparkContext, path_procesados)
              log("Archivo prepago generado")
            }

            //Tráfico cercano datos postpago
            val trafico_segmento_datos_post = ss.sql("select tgd.tele_numb || '|jvaldez|' from trafico_general_datos tgd where tipo_linea = 'Postpago'")
            trafico_segmento_datos_post.count()
            val Num_TCDPos = trafico_segmento_datos_post.count()
            log("total tráfico cercano postpago: " + Num_TCDPos)
            trafico_segmento_datos_post.show(5)

            if (Num_TCDPos > 0) {
              generateFile(trafico_cercano_datos, path_preprocesados + "/TMP", Nombre_archivo + fecha_reporte + "_POST", ss, ss.sparkContext, path_procesados)
              log("Archivo postpago generado")
            }

          }
            //===============================================
          //VOZ
          //===============================================
          log("---VOZ-------------------------------------------------")
          Try {
            val r = Seq("hdfs", "dfs", "-rm", path_preprocesados_tmp).!!
            }
          val df_voz = df_trafico.filter(col("msg").contains("VOZ"))
            .withColumn("calling_number", split(col("msg"), ",").getItem(1))
            .withColumn("called_number", split(col("msg"), ",").getItem(2))
            .withColumn("call_type", split(col("msg"), ",").getItem(3))
            .withColumn("imei", split(col("msg"), ",").getItem(4))
            .withColumn("imsi", split(col("msg"), ",").getItem(5))
            .withColumn("start_time", split(col("msg"), ",").getItem(6))
            .withColumn("time_stream", split(col("msg"), ",").getItem(6).cast("Double"))
            .withColumn("cellId", split(col("msg"), ",").getItem(7))
            .withColumn("idLac", split(col("msg"), ",").getItem(8))

          log("total trafico voz entrante: " + df_voz.count())
          val trafico_voz_tmp = df_voz.filter(col("time_stream").>(time_stream)).createOrReplaceTempView("trafico_voz_tmp")
          val test = ss.sql("select start_time from trafico_voz_tmp")
          test.show(10)

          val trafico_voz = ss.sql("with trafico_v as(select case " +
            "when call_type in ( '01','13') then calling_number else called_number end as tele_numb, " +
            "cellId , idLac, start_time FROM  trafico_voz_tmp), " +
            "trafico_ordenado as (select tele_numb, cellId , idLac, start_time, " +
            "row_number() over (partition by tele_numb, cellId, idLac  order by start_time desc) as order_trafico from trafico_v) " +
            " select * from trafico_ordenado where order_trafico = 1")
          trafico_voz.createOrReplaceTempView("trafico_voz")

          val total_traf_voz_reciente = trafico_voz.count()
          log("total trafico reciente voz: " + total_traf_voz_reciente)


          if (total_traf_voz_reciente > 0) {
            trafico_voz.show(10)
            val trafico_cercano_voz = ss.sql("select tc.tipo_linea, tv.tele_numb from  trafico_voz tv inner join tiendas_voz ta " +
              "on ta.celda= tv.cellId and ta.lac = tv.idLac inner join tabla_clientes tc on tv.tele_numb = tc.tele_numb")
            trafico_cercano_voz.createOrReplaceTempView("trafico_general_voz")
            val Num_TCV = trafico_cercano_voz.count()
            log("total tráfico cercano: " + Num_TCV)




            //Tráfico segmentación Prepago Voz
            val trafico_segmento_voz_prepago = ss.sql("select tgv.tele_numb || '|jvaldez|' from trafico_general_voz tgv where tipo_linea = 'Prepago'")
            val Num_TCVPre = trafico_segmento_voz_prepago.count()
            log("total tráfico cercano prepago: " + Num_TCVPre)
            trafico_segmento_voz_prepago.show(5)


            if (Num_TCVPre > 0) {
              generateFile(trafico_segmento_voz_prepago, path_preprocesados + "/TMP", Nombre_archivo + fecha_reporte + "_PREP", ss, ss.sparkContext, path_procesados)
              log("Archivo prepago generado")
            }


            //Tráfico segmentación Postpago Voz
            val trafico_segmento_voz_postpago = ss.sql("select tgv.tele_numb || '|jvaldez|' from trafico_general_voz tgv where tipo_linea = 'Postpago'")
            val Num_TCVPos = trafico_segmento_voz_postpago.count()
            log("total tráfico cercano postpago: " + Num_TCVPre)
            trafico_segmento_voz_prepago.show(5)


            if (Num_TCVPos > 0) {
              generateFile(trafico_segmento_voz_postpago, path_preprocesados + "/TMP", Nombre_archivo + fecha_reporte + "_POST", ss, ss.sparkContext, path_procesados)
              log("Archivo postpago generado")
            }

          }
        }
      }
      streamingContext.start()
      streamingContext.awaitTermination()

    }
    catch {
      case e: Throwable => log(e.getMessage)
        val des_error = "ERROR: Se presenta excepcion al ejecutar proceso: " + e.getMessage
        error_proceso(path_ejecuta_escenario, repo_sisnot, job_name, des_error)
    }
  }



  def deleteFolders(pathToDelete: String): Unit = {
    Try {
      val r = Seq("hdfs", "dfs", "-rm", pathToDelete + "/TMP/*").!!
    }
    Try {
      val r = Seq("hdfs", "dfs", "-rm", pathToDelete + "/TMP/_SUCCESS*").!!
    }
    Try {
      val r = Seq("hdfs", "dfs", "-rmdir", pathToDelete + "/TMP").!!
    }
  }



  def generateFile(df: DataFrame, folderTemp: String, filename: String, ss: SparkSession, sc: SparkContext, path: String) {
    log("Inicio Generación Archivo: " + filename)
    deleteFolders(path)
    val fs_ = FileSystem.get(new URI(folderTemp), ss.sparkContext.hadoopConfiguration)
    val folderTemp_ = new Path(folderTemp)
    if (fs_.exists(folderTemp_))
      fs_.delete(folderTemp_, false)
    df.repartition(1)
      .write
      .mode("overwrite")
      .format("csv")
      .option("compression", "none")
      .option("delimiter", "\t")
      .save(folderTemp)
    val conf = sc.hadoopConfiguration
    val src = new Path(folderTemp)
    val fs = src.getFileSystem(conf)
    val oneFile = fs.listStatus(src).map(x => x.getPath.toString()).find(x => x.endsWith("csv"))
    val srcFile = new Path(oneFile.getOrElse(""))
    val dest = new Path(path  +"/"+ filename + ".txt")
    fs.rename(srcFile, dest)
    deleteFolders(path)
    log("Fin Generación Archivo: " + filename)
  }
}

