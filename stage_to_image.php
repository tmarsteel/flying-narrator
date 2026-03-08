<?php
declare(strict_types=1);

$padding = 10;

$stage = json_decode(file_get_contents("php://stdin"));
$scale = 0.4;

$min_x = PHP_INT_MAX;
$max_x = PHP_INT_MIN;
$min_y = PHP_INT_MAX;
$max_y = PHP_INT_MIN;

$carry_point = [
  "x" => 0.0,
  "y" => 0.0,
  "z" => 0.0,
];
foreach ($stage->vectors as $vec) {
  $carry_point["x"] += $vec->x;
  $carry_point["y"] += $vec->y;
  $min_x = min($min_x, $carry_point["x"]);
  $max_x = max($max_x, $carry_point["x"]);
  $min_y = min($min_y, $carry_point["y"]);
  $max_y = max($max_y, $carry_point["y"]);
}

$offset_x = -$min_x;
$offset_y = -$min_y;
$width = $max_x - $min_x;
$height = $max_y - $min_y;

function track2image_x($xval) {
  global $offset_x, $scale, $padding;
  return intval(ceil(($xval + $offset_x) * $scale)) + $padding;
}
function track2image_y($yval) {
  global $offset_y, $scale, $padding;
  return intval(ceil(($yval + $offset_y) * $scale)) + $padding;
}

$image = imagecreatetruecolor(intval(ceil($width * $scale)) + $padding * 2, intval(ceil($height * $scale)) + $padding * 2);
$bgcolor = imagecolorallocate($image, 255, 255, 255);
$trackcolor = imagecolorallocate($image, 0, 0, 0);
$startcolor = imagecolorallocate($image, 0, 150, 0);
$finishcolor = imagecolorallocate($image, 200, 0, 0);
$markersize = intval(max($width, $height) / 25.0);
imagefilledrectangle($image, 0, 0, imagesx($image), imagesy($image), $bgcolor);
imageantialias($image, true);
imagesetthickness($image, 3);

$carry_point = [
  "x" => 0.0,
  "y" => 0.0,
  "z" => 0.0,
];
$prev_image_x = track2image_x($carry_point["x"]);
$prev_image_y = track2image_y($carry_point["y"]);
foreach ($stage->vectors as $vec) {
  $carry_point["x"] += $vec->x;
  $carry_point["y"] += $vec->y;
  $image_x = track2image_x($carry_point["x"]);
  $image_y = track2image_y($carry_point["y"]);
  imageline(
    $image,
    $prev_image_x,
    $prev_image_y,
    $image_x,
    $image_y,
    $trackcolor,
  );

  $prev_image_x = $image_x;
  $prev_image_y = $image_y;
}

imageellipse($image, track2image_x(0), track2image_y(0), $markersize, $markersize, $startcolor);
imageellipse($image, track2image_x($carry_point["x"]), track2image_y($carry_point["y"]), $markersize, $markersize, $finishcolor);

imagepng($image, "php://output");
