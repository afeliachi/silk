package org.silkframework.config

import java.io.File
import java.util.logging.Logger
import javax.inject.Named

import com.typesafe.config.{ConfigFactory, Config => TypesafeConfig}

/**
  * Holds the configuration properties
  */
trait Config {
  /** Returns an instance of the current [[TypesafeConfig]] */
  def apply(): TypesafeConfig

  /** Refreshes the Config instance, e.g. load from changed config file or newly set property values. */
  def refresh(): Unit
}

@Named("default")
class DefaultConfig extends Config {

  // Overwrite default logging pattern for java.util.logging
  if (System.getProperty("java.util.logging.SimpleFormatter.format") == null) {
    System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %3$s%n%4$s: %5$s%6$s%n")
  }

  private lazy val log = Logger.getLogger(this.getClass.getName)

  private var config = this.synchronized {
    init()
  }

  private def init(): TypesafeConfig = {
    this.synchronized {
      ConfigFactory.invalidateCaches()
      var fullConfig = ConfigFactory.load()
      // Check if we are running as part of the eccenca Linked Data Suite
      if (fullConfig.hasPath("elds.home") || System.getenv("ELDS_HOME")!=null) {
        val eldsHome = fullConfig.getString("elds.home")
        val eldsHomeEnv = System.getenv("ELDS_HOME")
        // Since elds.home is defined, the config should exist in the location given in elds.home or ELDS_HOME
        val configFile = if (new File(eldsHome + "/etc/dataintegration/dataintegration.conf").exists) {
          log.info(s"Configuration file found at $eldsHome:/etc/dataintegration/dataintegration.conf")
          new File(eldsHome + "/etc/dataintegration/dataintegration.conf")
        }
        else if (eldsHomeEnv!=null && new File(eldsHomeEnv + "/etc/dataintegration/dataintegration.conf").exists){
          log.info(s"Configuration file found at $eldsHomeEnv/etc/dataintegration/dataintegration.conf")
          new File(eldsHomeEnv + "/etc/dataintegration/dataintegration.conf")
        }
        else {
          new File(eldsHome + "/etc/dataintegration/dataintegration.conf")
        }

        if (!configFile.exists) {
          val msg = new StringBuilder
          msg ++= s"Mandatory configuration file not found at: ${configFile.getAbsolutePath} "
          if(eldsHomeEnv != null)
            msg ++= s"and $eldsHomeEnv/etc/dataintegration/dataintegration.conf "
          msg ++= "Possible fix: Map a volume with the config file to this location. "
          msg ++= "Otherwise set elds.home or $ELDS_HOME to point to the correct location."
          log.warning(msg.toString())
        }

        val eldsConfig = ConfigFactory.parseFile(configFile)
        fullConfig = eldsConfig.withFallback(fullConfig)
      }
      // if elds.home is not defined, we can't throw an exception, just a message
      else {
        Logger.getLogger(this.getClass.getName).info(
          "Variable $ELDS_HOME is not defined. If this application is not running in the ELDS context " +
          "you can ignore this warning. Otherwise please configure $ELDS_HOME or elds.home."
        )
      }
      // Check if we are running as part of the Play Framework
      val playConfig1 = new File(System.getProperty("user.home") + "/conf/reference.conf")
      val playConfig2 = new File(System.getProperty("user.home") + "/conf/application.conf")
      if (playConfig1.exists()) {
        fullConfig = fullConfig.withFallback(ConfigFactory.parseFile(playConfig1))
      }
      if (playConfig2.exists()) {
        fullConfig = fullConfig.withFallback(ConfigFactory.parseFile(playConfig2))
      }
      fullConfig.resolve()
    }
  }

  def apply(): TypesafeConfig = {
    this.synchronized {
      config
    }
  }

  /** Refreshes the Config instance, e.g. load from changed config file or newly set property values. */
  override def refresh(): Unit = {
    this.synchronized {
      config = init()
    }
  }
}

object DefaultConfig {
  // This default initialization needed for usages that don't involve dependency injection
  lazy val instance = new DefaultConfig()
}