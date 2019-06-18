package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Primitives;

/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@SuppressWarnings({ "rawtypes", "unchecked" })
class Dispatch{

  private final LoadingCache<CallSignature, BiFunction<Object, List<Object>, Object>> methodCache = CacheBuilder.newBuilder()
    .expireAfterAccess(60, TimeUnit.MINUTES)
    .build(CacheLoader.from(this::resolveMethod));

  public Dispatch(){
  }

  public Object invoke(Object o, String methodName, List<Object> valueStack){
    try{
      return methodCache.getUnchecked(makeSig(o, methodName, valueStack)).apply(o, valueStack);
    }catch(Throwable e){
      throw new RuntimeException(e);
    }
  }

  private boolean isCorrectArity(int arity, Executable e){
    if(e.isVarArgs())
      return e.getParameterTypes().length <= arity; // Still need a minimum number of args for varargs
    return e.getParameterTypes().length == arity; // If not varargs then the arity needs to be exactly right
  }

  private BiFunction<Object, List<Object>, Object> wrapMethodHandle(MethodHandle handle, Executable e){
    if(Modifier.isStatic(e.getModifiers()) || e instanceof Constructor){
      return (o, args) -> {
        try{
          return handle.invokeWithArguments(args);
        }catch(Throwable e1){
          throw new RuntimeException(e1);
        }
      };
    }
    return (o, args) -> {
      try{
        final Object[] argArr = new Object[args.size() + 1];
        argArr[0] = o;
        int idx = 1;
        for(Object arg : args)
          argArr[idx++] = arg;
        return handle.invokeWithArguments(argArr);
      }catch(Throwable e1){
        throw new RuntimeException(e1);
      }
    };
  }

  private BiFunction<Object, List<Object>, Object> wrapConstructor(Executable e){
    try{
      return wrapMethodHandle(MethodHandles.lookup().unreflectConstructor(((Constructor) e)), e);
    }catch(IllegalAccessException e1){
      throw new RuntimeException(e1);
    }
  }

  private BiFunction<Object, List<Object>, Object> wrapMethod(Executable e){
    try{
      return wrapMethodHandle(MethodHandles.lookup().unreflect((Method) e), e);
    }catch(IllegalAccessException e1){
      throw new RuntimeException(e1);
    }
  }

  private boolean testCastMatch(final CallSignature sig, Executable e){
    final Class callTypes[] = sig.argTypes;
    final Class argTypes[] = e.getParameterTypes();

    return IntStream.range(0, callTypes.length)
      .allMatch(i -> argTypes[i].isAssignableFrom(callTypes[i]));
  }

  private static int compareClasses(Class a, Class b){
    if(a.isArray() != b.isArray())
      return 0; // Incomparable
    if(a.isArray())
      return compareClasses(a.getComponentType(), b.getComponentType());
    return ComparisonChain.start()
      .compareFalseFirst(a.isInterface(), b.isInterface())
      // Choose the more specific option, if possible
      .compareTrueFirst(b.isAssignableFrom(a), a.isAssignableFrom(b))
      .result();
  }

  private static boolean classesAreComparable(Class a, Class b){
    if(a.equals(b))
      return true;
    if(a.isArray() != b.isArray())
      return false;
    if(a.isArray())
      return classesAreComparable(a.getComponentType(), b.getComponentType());
    return a.isAssignableFrom(b) || b.isAssignableFrom(a);
  }

  private static boolean argumentsAreComparable(Executable aE, Executable bE){
    if(aE.isVarArgs() != bE.isVarArgs())
      return false; // We don't care if they are or not, but they need to be the same

    final Class[] aArgs = aE.getParameterTypes(), bArgs = bE.getParameterTypes();
    if(aArgs.length != bArgs.length)
      return false;

    return IntStream.range(0, aArgs.length).allMatch(idx -> {
      final Class a = aArgs[idx], b = bArgs[idx];
      return classesAreComparable(a, b);
    });
  }

  private static <E extends Executable> int argumentPartialOrder(E aE, E bE){
    final Class[] a = aE.getParameterTypes(), b = bE.getParameterTypes();
    if(a.length != b.length)
      return 0; // Incomparable;
    return IntStream.range(0, a.length)
      .map(idx -> compareClasses(a[idx], b[idx]))
      // If either is equal to zero keep the opposite one
      // "Fall" to zero if the signs are NOT the same
      // Pick arbitrarily if the signs are the same
      .reduce(0, (x, y) -> x == 0 ? y : y == 0 ? x : x > 0 != y > 0 ? 0 : x);
  }

  private static boolean hasTotalOrderOverArgs(List<Executable> execs){
    return execs.size() < 2 || IntStream.range(1, execs.size())
      .allMatch(idx -> argumentsAreComparable(execs.get(idx - 1), execs.get(idx)));
  }

  private static Stream<Method> findPublicDeclarations(Method original, Class target){
    checkArgument(Modifier.isPublic(original.getModifiers()));
    if(Modifier.isPublic(target.getModifiers()))
      return Stream.of(original);
    return ClassHierarchyCollection.hiararchyStream(target)
      .distinct()
      .filter(c -> Modifier.isPublic(c.getModifiers()))
      .map(Class::getMethods)
      .flatMap(Arrays::stream)
      .filter(m -> original.getName().equals(m.getName()))
      .filter(m -> classesAreComparable(original.getReturnType(), m.getReturnType()))
      .filter(m -> argumentsAreComparable(original, m));
  }

  private BiFunction<Object, List<Object>, Object> resolveMethod(final CallSignature sig){
    final List<Executable> methods;
    final boolean isConstructor = sig.methodName.equals("new");

    if(isConstructor){
      methods = Arrays.stream(sig.targetType.getConstructors())
        .collect(Collectors.toList());
    }else{
      methods = Arrays.stream(sig.targetType.getMethods())
        .distinct()
        .flatMap(m -> findPublicDeclarations(m, sig.targetType))
        .collect(Collectors.toList());
    }

    final Set<Executable> potentials = methods.stream()
      .filter(m -> (isConstructor ? sig.targetType.getName() : sig.methodName).equals(m.getName()))
      .filter(e -> Modifier.isPublic(e.getModifiers()))
      .filter(e -> isCorrectArity(sig.argTypes.length, e))
      .collect(Collectors.toSet());

    final List<Executable> castMatches = potentials.stream()
      .filter(e -> testCastMatch(sig, e))
      .collect(Collectors.toList());

    if(castMatches.size() >= 1 && hasTotalOrderOverArgs(castMatches)){
      final Executable min;
      if(isConstructor){
        min = castMatches.stream()
          .min(Comparator.comparing(Function.identity(), Dispatch::argumentPartialOrder))
          .get();
      }else{
        min = ((Collection<Method>) (Collection) castMatches).stream()
          .min(Comparator.comparing(Function.<Method> identity(), Dispatch::argumentPartialOrder)
            .thenComparing(Method::getReturnType, Dispatch::compareClasses))
          .get();
      }

      return isConstructor ? wrapConstructor(min) : wrapMethod(min);
    }

    if(castMatches.size() > 1)
      throw new AmbigiousDispatchException(sig);

    // If there is no exact match then we have to attempt to coerce the types to match
    // If this doesn't produce exactly one target then we give up
    // Better to fail then produce an unpredictable result

    final List<Map.Entry<Executable, Function<List<Object>, List<Object>>>> coercions =
      potentials.parallelStream()
        .map(e -> Maps.immutableEntry(e, Optional.ofNullable(tryCoerceArgs(sig, e))))
        .filter(e -> e.getValue().isPresent())
        .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().get()))
        .collect(Collectors.toList());

    if(coercions.isEmpty())
      throw new UnknownDispatchException(sig);
    else if(coercions.size() > 1)
      throw new AmbigiousDispatchException(sig);

    final Map.Entry<Executable, Function<List<Object>, List<Object>>> target = coercions.get(0);
    final Function<List<Object>, List<Object>> coercion = target.getValue();
    final BiFunction<Object, List<Object>, Object> mh;
    if(isConstructor)
      mh = wrapConstructor(target.getKey());
    else
      mh = wrapMethod(target.getKey());

    return (o, args) -> mh.apply(o, coercion.apply(args));
  }

  private Function<Object, Object> coerceInterface(Class callType, Class argType){
    if(Map.class.isAssignableFrom(callType)){
      if(Iterable.class.equals(argType) || Collection.class.equals(argType) || List.class.equals(argType))
        return m -> ((Map) m).values().stream().collect(Collectors.toList());
      if(Set.class.equals(argType))
        return m -> ((Map) m).values().stream().collect(Collectors.toCollection(Sets::newLinkedHashSet));
      if(SortedSet.class.equals(argType) || NavigableSet.class.equals(argType))
        return m -> ((Map) m).values().stream().collect(Collectors.toCollection(TreeSet::new));
      if(SortedMap.class.equals(argType) || NavigableMap.class.equals(argType))
        return m -> new TreeMap((Map) m);
    }
    return null;
  }

  private Function<Object, Object> coerceType(Class callType, Class argType){
    if(Map.class.isAssignableFrom(callType)){
      if(Collection.class.isAssignableFrom(argType)) // Might be a concrete collection type!
        return m -> {
          try{
            Collection c = (Collection) argType.newInstance();
            c.addAll(((Map) m).values());
            return c;
          }catch(InstantiationException | IllegalAccessException e){
            throw new RuntimeException(e);
          }
        };

      if(Map.class.isAssignableFrom(callType)) // Might be a concrete map type!
        return m -> {
          try{
            Map c = (Map) argType.newInstance();
            c.putAll((Map) m);
            return c;
          }catch(InstantiationException | IllegalAccessException e){
            throw new RuntimeException(e);
          }
        };
    }

    if(BigDecimal.class.isAssignableFrom(argType))
      return TypeConversions::asBigDecimal;

    // Desperate
    if(Number.class.isAssignableFrom(argType))
      return ((Function) TypeConversions::asBigDecimal).andThen(PrimitiveUtils.narrower(argType));

    return null;
  }

  private Function<Object, Object> coerce(Class callType, Class argType){

    final boolean argIsPrimOrBoxed = Primitives.unwrap(argType).isPrimitive();
    argType = Primitives.wrap(argType);
    assert !callType.isPrimitive();
    final boolean callIsBoxed = Primitives.unwrap(callType).isPrimitive();

    if(argType.isAssignableFrom(callType))
      return Function.identity(); // No coercion needed for this arg

    if(Boolean.class == argType){
      assert Boolean.class != callType : "Should have been caught by identity";

      if(void.class == callType)
        return v -> false;
      if(Number.class.isAssignableFrom(callType))
        return n -> ((Number) n).intValue() != 0;

      return null; // Only accept numbers, nulls, and real booleans for boolean args
    }

    if(void.class == callType){ // Null call argument
      if(!argType.isPrimitive())
        return Function.identity();

      Object narrowedNull = PrimitiveUtils.narrower(callType).apply(new Long(0));
      return v -> narrowedNull;
    }

    // Prims (and boxes) merely need a potentially narrowing conversion
    if(argIsPrimOrBoxed && callIsBoxed)
      return PrimitiveUtils.narrower(callType);

    Function<Object, Object> potential;

    if(argType.isInterface()){ // Try to playcate common interface types
      potential = coerceInterface(callType, argType);
      if(null != potential)
        return potential;
    }

    potential = coerceType(callType, argType);
    if(null != potential)
      return potential;

    // At this point we are desperate
    if(CharSequence.class.isAssignableFrom(callType) && argIsPrimOrBoxed){
      potential = v -> new BigDecimal(v.toString());
      potential = potential.andThen(PrimitiveUtils.narrower(argType));
      return potential;
    }

    return null;
  }

  private Function<List<Object>, List<Object>> tryCoerceVarArgs(final CallSignature sig, final Executable e){
    final Class callTypes[] = sig.argTypes;
    final Class argTypes[] = e.getParameterTypes();
    final Class varargType = checkNotNull(callTypes[callTypes.length - 1]).getComponentType();

    // Remember varargs includes an empty array
    assert callTypes.length >= argTypes.length - 1;

    final List<Function<Object, Object>> argCoercions = ImmutableList.copyOf(
      IntStream.range(0, callTypes.length)
        .mapToObj(i -> coerce(callTypes[i], argTypes[i]))
        .collect(Collectors.toList()));

    final BiConsumer<Iterator<Object>, List<Object>> varargHandler;
    if(callTypes.length < argTypes.length){
      varargHandler = (itr, l) -> {
        checkState(!itr.hasNext());
        l.add(Array.newInstance(varargType, 0));
      };
    }else if(callTypes.length == argTypes.length
      && callTypes[callTypes.length].isArray()
      && callTypes[callTypes.length].getComponentType() == varargType){
      varargHandler = (itr, l) -> l.add(itr.next());
    }else{
      varargHandler = (itr, l) -> l.add(Iterators.toArray(itr, varargType));
    }

    return args -> {
      List<Object> l = Lists.newArrayListWithExpectedSize(callTypes.length);
      Iterator<Object> argItr = args.iterator();
      Iterator<Function<Object, Object>> cItr = argCoercions.iterator();
      while(cItr.hasNext())
        l.add(cItr.next().apply(argItr.next()));

      varargHandler.accept(argItr, l);

      return l;
    };
  }

  private Function<List<Object>, List<Object>> tryCoerceArgs(final CallSignature sig, final Executable e){
    if(e.isVarArgs())
      return tryCoerceVarArgs(sig, e);

    final Class callTypes[] = sig.argTypes;
    final Class argTypes[] = e.getParameterTypes();
    assert callTypes.length == argTypes.length;

    List<Function<Object, Object>> argCoercions =
      IntStream.range(0, callTypes.length)
        .mapToObj(i -> coerce(callTypes[i], argTypes[i]))
        .collect(Collectors.toList());

    if(argCoercions.contains(null))
      return null;

    final List<Function<Object, Object>> immutableArgCoercions = ImmutableList.copyOf(argCoercions);

    return args -> {
      Object[] argCopy = args.toArray();
      IntStream.range(0, args.size())
        .forEach(idx -> argCopy[idx] = immutableArgCoercions.get(idx).apply(argCopy[idx]));
      return Arrays.asList(argCopy);
    };
  }

  private static Class getType(Object o){
    return null == o ? void.class : o.getClass();
  }

  private CallSignature makeSig(Object o, String methodName, List<Object> valueStack){
    return new CallSignature(o, methodName,
      valueStack.stream()
        .map(Dispatch::getType));
  }

  private static final HashFunction sigHashF = Hashing.goodFastHash(32);

  private class CallSignature{
    private final String methodName;
    // private final boolean isStatic;
    private final Class targetType;
    private final Class[] argTypes;

    private final int hash;

    CallSignature(Object target, String name, Stream<Class> argumentTypes){
      checkNotNull(target);
      if(target instanceof ClassWrapper)
        this.targetType = ((ClassWrapper) target).getClazz();
      else
        this.targetType = target.getClass();

      methodName = checkNotNull(name.trim());
      checkArgument(!methodName.isEmpty());
      this.argTypes = argumentTypes.toArray(Class[]::new);

      hash = sigHashF.newHasher()
        .putString(methodName, Charsets.UTF_8)
        .putInt(Arrays.hashCode(argTypes))
        .putInt(targetType.hashCode())
        .hash().asInt();
    }

    @Override
    public boolean equals(Object obj){
      if(obj instanceof CallSignature){
        CallSignature s = (CallSignature) obj;
        return s == this || (hash == s.hash
          && targetType.equals(s.targetType)
          && methodName.equals(s.methodName)
          && Arrays.equals(argTypes, s.argTypes));
      }
      return false;
    }

    @Override
    public int hashCode(){
      return hash;
    }

    @Override
    public String toString(){
      return String.format("%s::%s(%s)", targetType.getName(), methodName,
        Arrays.stream(argTypes).map(Class::getName).collect(Collectors.joining(", ")));
    }
  }

  private static class AmbigiousDispatchException extends RuntimeException{
    private static final long serialVersionUID = 1L;

    public AmbigiousDispatchException(CallSignature sig){
      super("Unable to unambigiously determine target for the caller signature " + sig.toString());
    }
  }

  private static class UnknownDispatchException extends RuntimeException{
    private static final long serialVersionUID = 1L;

    public UnknownDispatchException(CallSignature sig){
      super("Unable to find any potential call site for " + sig.toString());
    }
  }

}
