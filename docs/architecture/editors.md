# Editors

- **An editor reads the value and hands one back.** `ctx.value(T.class)`, `ctx.set(value)`; a value the host
  could not read is *shown* (`Slots.sourceOr`) and never overwritten. A run of pictures is `SlotRun.Element`s
  (`Element.of`).
- **Two rules for any row over a run**: an element it cannot read is kept as it stands, because every write
  hands back the whole list; and *Remove* is **disabled** at `run.minimum()`, with the reason in its label.
- **A list that moves is read when the dropdown opens, never when the block is drawn**, and the box stays
  typeable (`Editors.choiceSlot`) — the activity and outcome pickers read `FlowValue.read(ctx.services())`.
- **Every screen pick asks where first**: Point, Rect, Size and the eyedropper go through
  `plugin/source/SurfaceMenu` (the bot's source, another window or screen, the whole desktop) and a frozen
  frame (`plugin/screen/FrameShotSource`), and `plugin/screen/PickSpace` decides from the call whether the
  numbers are relative to that surface or desktop pixels. The Precision dialog draws its matches on that frame
  (`MatchOverlay`), reads its target colour from the call (`SlotContext.argumentValue`) and learns ΔE from pins
  (`ToleranceTeacher`).
