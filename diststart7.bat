set CURRDIR=%CD%
set PATH=%CURRDIR%\dist2\dixi;%CURRDIR%\dist2\audimus;%PATH%
:: ;%CURRDIR%\dist2\flite
set JAVACMD=%CURRDIR%\dist2\jre7\bin\java.exe

chcp 1252

::ping -n 11 127.0.0.1 > nul

:loop

%JAVACMD% -Xmn100M -Xms200M -Xmx200M -Dfile.encoding=ISO-8859-1 -jar %CURRDIR%\dist\eTutor.jar %CURRDIR%\dist2\config\local.properties lang=en asr tts qa
:: tts asr qa
:: ,en,es
ping -n 61 127.0.0.1 > nul

goto loop

pause
