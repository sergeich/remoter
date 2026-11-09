Change Log
==========

Version 3.0.0-beta *(2026-11-08)*
---------------------------------
Defer @Remoter interfaces with unresolved types to next KSP round


Version 3.0.0-alpha *(2026-03-17)*
----------------------------------
Fork published as me.sergeich:remoter. Kotlin and KSP only, Java and KAPT support dropped
Support for suspend functions with varargs parameters
Fix ServiceConnector race condition on reconnect


Version 2.0.10 *(2025-12-18)*
-----------------------------
Handle same process for last method lookup


Version 2.0.9 *(2025-11-14)*
----------------------------
Fix for multi version support from multiple clients.


Version 2.0.6 *(2024-07-26)*
----------------------------
Fix for generic typed interfaces, updated dependency versions.


Version 2.0.5 *(2023-05-15)*
----------------------------
Option to specify flags in ServiceConnector and auto generated Proxy constructors for kotlin


Version 2.0.3 *(2021-09-16)*
----------------------------
Handling service disconnections from ServiceConnector
Added more sample and tests


Version 2.0.3 *(2021-09-16)*
----------------------------
Handling service disconnections from ServiceConnector
Added more sample and tests


Version 2.0.0 *(2020-02-13)*
----------------------------
Kotlin suspend function support!


Version 1.2.6 *(2020-01-25)*
----------------------------
Proxy equals now match with the id of the stub process it wraps
Better Proxy toString 

Version 1.2.4 *(2019-06-14)*
----------------------------
Propagate exception from @Oneway 

Version 1.2.0 *(2019-02-11)*
----------------------------
Prevent name collisions


Version 1.1.9 *(2018-01-12)*
----------------------------

Support for binder[]
cleanup methods for proxy/stub


Version 1.1.8 *(2018-08-28)*
----------------------------

Proxy returned of same service instance will have the same identity


Version 1.1.5 *(2018-02-02)*
----------------------------

All Proxy now implement RemoteProxy interface that exposes methods to listen for binder death


Version 1.1.2 *(2017-10-7)*
----------------------------

Clients get the same exception that is thrown by the remote implementation.


Version 1.1.0 *(2017-10-4)*
----------------------------

Supports extending other interfaces

Version 1.0.4 *(2017-09-30)*
----------------------------

Exposes methods in proxy to listen for remote process death


Version 1.0.2 *(2017-09-21)*
----------------------------

Support for Parceler (@Parcel)


Version 1.0.1 *(2017-09-16)*
----------------------------

Support for @Oneway to mark a method as asynchronous


Version 1.0.0 *(2017-08-23)*
----------------------------

Initial release.
