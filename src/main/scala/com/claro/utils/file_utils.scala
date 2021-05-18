package com.claro.utils

import java.lang.Math.{atan2, cos, sin, sqrt}
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar

import com.claro.utils.notifications.log
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf

import scala.collection.mutable
import scala.sys.process._
import scala.util.Try


class file_utils(protected val ss: SparkSession, protected val path: String) extends Serializable {

  def listHDFSFolders(path: String): Array[String] = {

    val r = Seq("hdfs", "dfs", "-ls", path).!!
    val s = r.split("\n")
    s.filter(line => !line.equals(s.head)).map(line => line.split(" +").last)


  }

  def isPathExist(path: String): Boolean = {
    try {
      FileSystem.get(new URI(path), new Configuration()).exists(new Path(path))
    } catch {
      case _: Throwable => false
    }
  }

  def isPathNotEmpty(path: String): Boolean = {
    try {
      FileSystem.get(new URI(path), new Configuration()).listStatus(new Path(path)).length > 0
    } catch {
      case _: Throwable => false
    }
  }



  def createFolder(path: String): Unit = {
    if (!isPathExist(path))
      FileSystem.get(new URI(path), ss.sparkContext.hadoopConfiguration).mkdirs(new Path(path))
  }


  def deletePath(path:String): Unit =
  {
    Try{
      val deleteFiles = "hdfs dfs -rm " + path + "/*"
      log(deleteFiles)
      deleteFiles.!
      val deleteFolder = "hdfs dfs -rmdir " + path
      log(deleteFolder)
      deleteFolder.!
    }

  }

  def dGPSDistance(dLat1: Double, dLon1: Double, dLat2: Double, dLon2: Double): Double = {
    val R = 6371 // km
    val PI = 3.14159265358979323846
    // Use Haversine formula to calculate distance (in km) between two points specified by
    // latitude/longitude (in numeric degrees).
    // It is the shortest distance over the earth surface.
    val dLat = (dLat2 - dLat1) * (PI / 180.0)
    val dLon = (dLon2 - dLon1) * (PI / 180.0)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(dLat1 * (PI / 180.0)) * cos(dLat2 * (PI / 180.0)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    // Distance in km
    val d = R * c
    // Return distance in meters
    d * 1000.0
  }



}
