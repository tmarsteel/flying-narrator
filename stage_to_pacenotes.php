<?php
declare(strict_types=1);

$stage = json_decode(file_get_contents("php://stdin"));

function vector_angle_single($a): float {
  return atan2($a->x, $a->y);
}

/**
 * let P be the point Origin + $a,
 * then this function returns the angle between the straights Origin->P
 * and P->(Origin+$b). And always the smaller one.
 * @return the angle, -180° - 180°
 */
function vector_angle($a, $b): float {
  $angle = vector_angle_single($b) - vector_angle_single($a);
  if ($angle > M_PI) {
    $angle -= M_PI * 2;
  } elseif ($angle < -M_PI) {
    $angle += M_PI * 2;
  } else if ($angle == -M_PI) {
    $angle = M_PI;
  }
  return $angle;
}

function vector_len($v): float {
  return sqrt($v->x * $v->x + $v->y * $v->y + $v->z * $v->z);
}

$travelled = 0;
$previous_vec = null;
foreach ($stage->vectors as $vec) {
  $len = vector_len($vec);
  echo "$travelled,";

  if ($previous_vec == null) {
    echo "0";
  } else {
    $angle = rad2deg(vector_angle($previous_vec, $vec));
    $curvature = $angle / $len;
    echo $curvature;
  }
  echo PHP_EOL;

  $previous_vec = $vec;
  $travelled += $len;
}

//---- tests
function assert_($val) {
  if ($val) return;
  throw new Exception("Assertion error");
}
function test_vec_angle() {
  $up = vec(0, 1);
  $up_right = vec(1, 1);
  $right = vec(1, 0);
  $down_right = vec(1, -1);
  $down = vec(0, -1);
  $down_left = vec(-1, -1);
  $left = vec(-1, 0);
  $up_left = vec(-1, 1);

  assert_(rad2deg(vector_angle($up, $up)) == 0);
  assert_(rad2deg(vector_angle($up, $up_right)) == 45);
  assert_(rad2deg(vector_angle($up_right, $up)) == -45);
  assert_(rad2deg(vector_angle($down_right, $down_left)) == 90);
  assert_(rad2deg(vector_angle($down_left, $down_right)) == -90);

  assert_(rad2deg(vector_angle($up, $down)) == 180);
  assert_(rad2deg(vector_angle($down, $up)) == 180);
}

function vec($x, $y) {
  $vec = new stdClass;
  $vec->x = $x;
  $vec->y = $y;
  return $vec;
}
