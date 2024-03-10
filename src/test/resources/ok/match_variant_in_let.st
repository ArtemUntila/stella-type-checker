language core;

extend with #variants, #unit-type, #let-bindings;

fn attempt(get_one? : Bool) -> <| value : Nat, failure : Nat |> {
  return
    if get_one?
      then <| value = succ(0) |>
      else <| failure = 0 |>
}

fn main(succeed : Bool) -> Nat {
  return let a = match attempt(succeed) {
      <| value = n |> => succ(n)
    | <| failure = _ |> => 0
  } in a
}