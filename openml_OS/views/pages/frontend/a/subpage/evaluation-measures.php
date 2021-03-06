 <?php if(false !== strpos($_SERVER['REQUEST_URI'], '/a/evaluation-measures/')){ ?>
	<?php if(!isset( $this->measure)) echo 'Sorry, this measure is not known.'; else { ?>
  <h1><?php echo str_replace('-',' ',$this->measure['name']); ?></h1>
  <div class="panel">

	<p><?php echo $this->measure['description']; ?></p>
	<ul class="hotlinks">
		<li><a href="https://github.com/openml/OpenML/tree/master/Java/OpenmlWebapplication/src/org/openml/webapplication/evaluate"><i class="fa fa-gears"></i> View code</a></li>
	</ul>
	<h2>Properties</h2>
		<div class='table-responsive'><table class='table table-striped'>
		<tr><td>Minimum value</td><td><?php echo $this->measure['min']; ?></td></tr>
		<tr><td>Maximum value</td><td><?php echo $this->measure['max']; ?></td></tr>
		<tr><td>Unit</td><td><?php echo $this->measure['unit']; ?></td></tr>
		<tr><td>Optimization</td><td><?php if( 1 == $this->measure['higherIsBetter']) echo 'Higher is better'; elseif( 0 == $this->measure['higherIsBetter']) echo 'Lower is better'; ?></td></tr>

		</table></div>
  </div>

	<?php }} ?>
