# http://developer.android.com/guide/developing/tools/proguard.html

-dontwarn **

# DbSet wraps every Room DAO with a java.lang.reflect.Proxy. Keep the DAO
# contracts as interfaces so Proxy.newProxyInstance can use their runtime type.
-keep @androidx.room.Dao interface *
