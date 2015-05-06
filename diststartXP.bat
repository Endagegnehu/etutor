%windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks
%windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks
%windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks
%windir%\system32\rundll32.exe advapi32.dll,ProcessIdleTasks

set CURRDIR=%CD%
set PATH=%CURRDIR%\dist2\dixi;%CURRDIR%\dist2\audimus;%PATH%
:: set PATH=%CURRDIR%\lib\bin;%PATH%
set JAVACMD=%CURRDIR%\dist2\jre7\bin\java.exe

chcp 437

%JAVACMD% -Xmn100M -Xms200M -Xmx200M -Dfile.encoding=ISO-8859-1 -jar %CURRDIR%\dist\Wellcome.jar %CURRDIR%\dist2\config\local.properties

pause