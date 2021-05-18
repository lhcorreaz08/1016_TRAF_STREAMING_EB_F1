package com.claro

import com.claro.utils.notifications.log
import org.apache.spark.sql.SparkSession
import scala.sys.process._

class sisnot_processor(protected val ss: SparkSession) {

  import ss.implicits._

  private var paramsDf = this.ss.emptyDataFrame

  /*
  *Name: getparamsFile
  *Description: Crea el archivo .txt en el servidor con los valores obtenidos desde la base de datos de SISNOT,
  *una vez creado el archivo se hace un put hacia el HDFS
*/
  def getparamsFile(path_extrae_pr: String, path_File: String, environment: String, job: String, ext: String, tmp_folder: String): Unit = {
    val txt_sisnot = "java -jar " + path_extrae_pr + " " + environment + " " + job + " " + ext + " " + path_File + "/" + job + "." + ext
    log(txt_sisnot)
    txt_sisnot.!
    val put_sisnot = "hdfs dfs -put -f " + path_File + "/" + job + "." + ext + " " + path_File + "/" + tmp_folder + "/"
    log(put_sisnot)
    put_sisnot.!
  }

  /*
  *Name: getParamsDF
  *Description: Esta funcion carga en memoria los parametros del sisnot del archivo .txt del HDFS
  */
  def getParamsDF(path_file_sisnot: String) {
    val newNames = Seq("JOB", "VARIABLE", "VALOR")
    val rdd = ss.sparkContext.textFile(path_file_sisnot)
    val df = rdd.map(x => x.split("\\|\\|")).map(x => (x(0), x(1), x(2))).toDF(newNames: _*)
    paramsDf = df

  }

  /*
  * Name:getParamValue
  * Description: Obtiene el valor de una variable en especifico
  * job: Nombre del job almacenado en SISNOT
  * variable: Nombre de la variable a leer
  */
  def getParamValue(job: String, variable: String): String = {
    val valor = paramsDf.filter("JOB = '" + job + "' AND VARIABLE = '" + variable + "'").collect()(0)(2).toString()
    log(variable + ": "+ valor)
    return valor
  }

}
