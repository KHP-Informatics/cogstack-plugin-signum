# cogstack-plugin-signum

A plugin for https://github.com/KHP-Informatics/cogstack for use in SIGNUM project (at UCL)

## How to use:

```
java -Dloader.path=<PATH_TO_PLUGIN_JAR> -cp <COGSTACK_JAR> org.springframework.boot.loader.PropertiesLauncher <CONFIGS>
```

This is needed because -jar can only work with one JAR.

## Beauty:

1. Site specific features can be developed separately and loaded as JAR.
2. Plugins used completely configurable.

## Ugly / To be improved:

1. Need to duplicate Document.groovy because there is no "core-lib" for these core classes in the pipeline.

Reference:
* http://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/loader/PropertiesLauncher.html
* https://github.com/KHP-Informatics/cogstack/issues/17
* https://github.com/KHP-Informatics/cogstack/issues/18
