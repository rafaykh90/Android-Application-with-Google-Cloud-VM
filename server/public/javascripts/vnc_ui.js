$(document).ready(function() {

  // Set canvas size to window size
  $('#noVNC_canvas').load(function(){
    $('#noVNC_canvas').width($(window).width())
    $('#noVNC_canvas').height($(window).height())
  })

  $(window).resize(function() {
    $('#noVNC_canvas').width($(window).width())
    $('#noVNC_canvas').height($(window).height())
  });

  $('#vnc-disconnect').click(function() {
    $.get('/stopvm/'+ window.VNCapplication, function() {
      window.location.replace('/home');
    });
  });

});
