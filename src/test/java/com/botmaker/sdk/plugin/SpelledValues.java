package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;

import java.util.List;
import java.util.Optional;

/**
 * A test host's {@link PluginValues} kept by spelling — a value's id, a member as {@code "Group.NAME"} — as Studio's
 * own keeps them: each typed operation calls the spelled one a fake overrides, so a fake records and answers in the
 * strings a test asserts on. A spelled operation a fake leaves alone answers what a host with no source tree does.
 */
public abstract class SpelledValues implements PluginValues {

    public abstract Optional<ValueContext> open(String id);

    public Optional<String> create(String id) { return Optional.of(NO_SOURCE_TREE); }

    public List<String> members(String id) { return List.of(); }

    public Optional<ValueContext> open(String id, String member) { return Optional.empty(); }

    public Optional<String> add(String id, String member, Object value) { return Optional.of(NO_SOURCE_TREE); }

    public List<Use> uses(String id, String member) { return List.of(); }

    public Optional<String> rename(String id, String member, String newName) { return Optional.of(NO_SOURCE_TREE); }

    public Optional<String> repoint(String id, String member, String replacement, String note) {
        return Optional.of(NO_SOURCE_TREE);
    }

    public Optional<String> remove(String id, String member) { return Optional.of(NO_SOURCE_TREE); }

    public Optional<String> moveBefore(String id, String member, String before) { return Optional.of(NO_SOURCE_TREE); }

    public List<String> groups(String id) { return List.of(); }

    public Optional<String> addGroup(String id, String group, List<String> constants) {
        return Optional.of(NO_SOURCE_TREE);
    }

    public Optional<String> renameGroup(String id, String group, String newName) { return Optional.of(NO_SOURCE_TREE); }

    public Optional<String> removeGroup(String id, String group) { return Optional.of(NO_SOURCE_TREE); }

    /** The methods returning {@code group} of the set {@code id}; both {@code null} for the {@code void} ones. */
    public List<String> methods(String id, String group) { return List.of(); }

    /** As {@link #methods(String, String)}: both {@code null} for a {@code void} method. */
    public MethodWrite createMethod(String id, String group, String className, String method) {
        return new MethodWrite(null, NO_SOURCE_TREE);
    }

    @Override public final Optional<ValueContext> open(ManagedValue<?> value) { return open(value.id()); }

    @Override public final Optional<String> create(ManagedValue<?> value) { return create(value.id()); }

    @Override public final List<Member> members(ManagedValue<?> set) {
        return members(set.id()).stream().flatMap(spelled -> Member.read(spelled).stream()).toList();
    }

    @Override public final Optional<ValueContext> open(ManagedValue<?> set, Member member) {
        return open(set.id(), member.toString());
    }

    @Override public final <E> Optional<String> add(ManagedValue<E> set, Member member, E value) {
        return add(set.id(), member.toString(), value);
    }

    @Override public final List<Use> uses(ManagedValue<?> set, Member member) {
        return uses(set.id(), member.toString());
    }

    @Override public final Optional<String> rename(ManagedValue<?> set, Member member, String newName) {
        return rename(set.id(), member.toString(), newName);
    }

    @Override public final Optional<String> repoint(ManagedValue<?> set, Member member, Member replacement,
                                                    String note) {
        return repoint(set.id(), member.toString(), replacement.toString(), note);
    }

    @Override public final Optional<String> remove(ManagedValue<?> set, Member member) {
        return remove(set.id(), member.toString());
    }

    @Override public final Optional<String> moveBefore(ManagedValue<?> set, Member member, Member before) {
        return moveBefore(set.id(), member.toString(), before.toString());
    }

    @Override public final List<String> groups(ManagedValue<?> set) { return groups(set.id()); }

    @Override public final Optional<String> addGroup(ManagedValue<?> set, String group, List<String> constants) {
        return addGroup(set.id(), group, constants);
    }

    @Override public final Optional<String> renameGroup(ManagedValue<?> set, String group, String newName) {
        return renameGroup(set.id(), group, newName);
    }

    @Override public final Optional<String> removeGroup(ManagedValue<?> set, String group) {
        return removeGroup(set.id(), group);
    }

    @Override public final List<String> methods(ManagedValue<?> set, String group) {
        return methods(set.id(), group);
    }

    @Override public final List<String> voidMethods() { return methods((String) null, null); }

    @Override public final MethodWrite createMethod(ManagedValue<?> set, String group, String className,
                                                    String method) {
        return createMethod(set.id(), group, className, method);
    }

    @Override public final MethodWrite createVoidMethod(String className, String method) {
        return createMethod((String) null, null, className, method);
    }
}
