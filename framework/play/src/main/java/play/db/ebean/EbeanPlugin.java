package play.db.ebean;

import play.*;
import play.db.*;

import play.core.*;
import play.api.libs.Files;

import java.io.*;
import java.util.*;

import com.avaje.ebean.*;
import com.avaje.ebean.config.*;
import com.avaje.ebeaninternal.server.ddl.*;
import com.avaje.ebeaninternal.api.*;

public class EbeanPlugin extends Plugin {
    
    final Application application;
    
    public EbeanPlugin(Application application) {
        this.application = application;
    }
    
    // --
    
    final Map<String,EbeanServer> servers = new HashMap<String,EbeanServer>();
    
    public void onStart() {

        Configuration ebeanConf = Configuration.root().getSub("ebean");
        
        if(ebeanConf != null) {
            for(String key: ebeanConf.keys()) {
                
                ServerConfig config = new ServerConfig();
                config.setName(key);
                try {
                    config.setDataSource(DB.getDataSource(key));
                } catch(Exception e) {
                    throw ebeanConf.reportError(
                        key,
                        e.getMessage(),
                        e
                    );
                }
                if(key.equals("default")) {
                    config.setDefaultServer(true);
                }
                
                String load = ebeanConf.getString(key);
                if(load.equals("auto")) {
                    
                } else {
                    String[] classes = load.split(",");
                    for(String clazz: classes) {
                        try {
                            config.addClass(Class.forName(clazz, true, application.classloader()));
                        } catch(Exception e) {
                            throw ebeanConf.reportError(
                                key,
                                "Cannot register class [" + clazz + "] in Ebean server",
                                e
                            );
                        }
                    }
                }
                
                servers.put(key, EbeanServerFactory.create(config));
                
                // DDL
                File evolutions = application.getFile("db/evolutions/" + key + "/1.sql");
                if(!evolutions.exists() || Files.readFile(evolutions).startsWith("# --- Created by Ebean DDL")) {
                    Files.createDirectory(application.getFile("db/evolutions/" + key));
                    Files.writeFileIfChanged(evolutions, generateEvolutionScript(servers.get(key), config));
                }

            }
        }
        
    }
    
    public static String generateEvolutionScript(EbeanServer server, ServerConfig config) {
        DdlGenerator ddl = new DdlGenerator((SpiEbeanServer)server, config.getDatabasePlatform(), config);
        String ups = ddl.generateCreateDdl();
        String downs = ddl.generateDropDdl();
        
        return (
            "# --- Created by Ebean DDL\n" +
            "# To stop Ebean DDL generation, remove this comment and start using Evolutions\n" +
            "\n" + 
            "# --- !Ups\n" +
            "\n" + 
            ups +
            "\n" + 
            "# --- !Downs\n" +
            "\n" +
            downs
        );
    }
    
    
}