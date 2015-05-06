:: %windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks
:: %windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks
:: %windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks
:: %windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks

set CURRDIR=%CD%
set PATH=%CURRDIR%\dist2\audimus;%PATH%
:: set PATH=%CURRDIR%\lib\bin;%PATH%
:: set JAVACMD=%CURRDIR%\dist2\jre7\bin\java.exe
set JAVA64CMD=%CURRDIR%\dist2\jre7\bin\java.exe

chcp 437

:: start %JAVACMD% -Dfile.encoding=ISO-8859-1 -jar %CURRDIR%\dist\eTutor.jar %CURRDIR%\dist2\config\tts.properties tts

%JAVA64CMD% -Xmx1024m -Dfile.encoding=ISO-8859-1 -jar %CURRDIR%\dist\eTutor.jar %CURRDIR%\dist2\config\local.properties lang=es tts

:: %JAVACMD% -Xmn50M -Xms100M -Xmx100M -Dfile.encoding=ISO-8859-1 -jar %CURRDIR%\dist\eTutor.jar %CURRDIR%\dist2\config\local.properties lang=es tts asr qa 
:: 
:: preload
:: > logs\log%date:~6,4%-%date:~3,2%-%date:~0,2%_%time:~0,2%-%time:~3,2%.txt 2>&1 
pause
