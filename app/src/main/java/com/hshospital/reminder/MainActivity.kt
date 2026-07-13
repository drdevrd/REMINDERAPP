Skip to content
drdevrd
REMINDERAPP
Repository navigation
Code
Issues
Pull requests
Actions
Projects
Wiki
Security and quality
Insights
Settings
Build APK
Update activity_main.xml #47
All jobs
Run details
Annotations
1 error and 1 warning
build
failed 4 minutes ago in 53s
Search logs
3s
1s
0s
0s
45s
> Task :app:mergeDebugResources FAILED

[Fatal Error] activity_main.xml:1:1: Content is not allowed in prolog.
> Task :app:parseDebugLocalResources FAILED


FAILURE: Build completed with 2 failures.

1: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:mergeDebugResources'.
> A failure occurred while executing com.android.build.gradle.internal.res.ResourceCompilerRunnable
   > Resource compilation failed (Failed to compile resource file: /home/runner/work/REMINDERAPP/REMINDERAPP/app/src/main/res/layout/activity_main.xml: . Cause: javax.xml.stream.XMLStreamException: ParseError at [row,col]:[1,1]
     Message: Content is not allowed in prolog.). Check logs for more details.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
==============================================================================

2: Task failed with an exception.
-----------
* What went wrong:
Execution failed for task ':app:parseDebugLocalResources'.
> A failure occurred while executing com.android.build.gradle.internal.res.ParseLibraryResourcesTask$ParseResourcesRunnable
   > Failed to parse XML file '/home/runner/work/REMINDERAPP/REMINDERAPP/app/build/intermediates/packaged_res/debug/layout/activity_main.xml'

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
==============================================================================

* Get more help at https://help.gradle.org

BUILD FAILED in 43s
> Task :app:processDebugMainManifest
9 actionable tasks: 9 executed
Error: Process completed with exit code 1.
0s
1s
0s
0s
