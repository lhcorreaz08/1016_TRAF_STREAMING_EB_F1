package com.claro.utils

import scala.collection.mutable

object parameteres {

  def getOptionParameters(map: mutable.Map[Symbol, String], list: List[String]): mutable.Map[Symbol, String] = {
    list match {
      case Nil => map
      case "--app_name" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('app_name -> value.trim), tail)
      case "--job_name" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('job_name -> value.trim), tail)
      case "--sisnot_repositorio" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('sisnot_repositorio -> value.trim), tail)
      case "--ambiente_sisnot_variables" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('ambiente_sisnot_variables -> value.trim), tail)
      case "--ambiente_sisnot_notificaciones" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('ambiente_sisnot_notificaciones -> value.trim), tail)
      case "--path_extrae_pr" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('path_extrae_pr -> value.trim), tail)
      case "--path_ejecuta_escenario" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('path_ejecuta_escenario -> value.trim), tail)
      case "--path_fuentes" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('path_fuentes -> value.trim), tail)
      case "--topic_voz" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('topic_voz -> value.trim), tail)
      case "--topic_datos" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('topic_datos -> value.trim), tail)
      case "--group_id" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('group_id -> value.trim), tail)
      case "--brokers_server" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('brokers_server -> value.trim), tail)
      case "--streaming_context_time" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('streaming_context_time -> value.trim), tail)
      case "--minutos_consulta" :: value :: tail => getOptionParameters(map ++ mutable.HashMap('minutos_consulta -> value.trim), tail)

      case option :: _ => println("Unknown option " + option)
        sys.exit(1)
    }
  }
}
